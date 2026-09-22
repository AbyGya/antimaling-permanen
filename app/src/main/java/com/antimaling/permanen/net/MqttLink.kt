package com.antimaling.permanen.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.antimaling.permanen.control.CommandHandler
import com.antimaling.permanen.control.FlashManager
import com.antimaling.permanen.control.RingManager
import com.antimaling.permanen.util.Prefs
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Link HP <-> laptop via MQTT publik (tanpa daftar, tanpa API key, tanpa console).
 * - HP subscribe am/{kode}/cmd, publish hasil ke am/{kode}/res
 * - Status online akurat via retained message + LWT (last will)
 * - Paho auto-reconnect + watchdog GuardService
 */
object MqttLink {
    const val HOST = "tcp://broker.emqx.io:1883"

    @Volatile private var client: MqttClient? = null
    @Volatile private var liveCode: String = ""
    @Volatile var lastError: String = ""
    @Volatile private var beatGen = 0

    fun cmdTopic(code: String) = "am/$code/cmd"
    fun resTopic(code: String) = "am/$code/res"
    fun statusTopic(code: String) = "am/$code/status"

    fun connected(): Boolean = try { client?.isConnected == true } catch (_: Exception) { false }

    fun currentCode(): String = liveCode

    /** Idempoten & aman dipanggil dari mana saja. */
    fun start(c: Context) {
        val app = c.applicationContext
        Thread {
            try { ensure(app) } catch (e: Exception) {
                lastError = e.message ?: "err"
            }
        }.apply { isDaemon = true; name = "mqtt-link"; start() }
    }

    /** Ganti kode -> putus, sambung ulang dengan kode baru. */
    fun reconnect(c: Context) {
        try { liveCode = "" } catch (_: Exception) {}
        try { try { client?.disconnectForcibly() } catch (_: Exception) {} } catch (_: Exception) {}
        try { client = null } catch (_: Exception) {}
        start(c)
    }

    fun stop() {
        try { try { client?.disconnect() } catch (_: Exception) {} } catch (_: Exception) {}
        try { client = null } catch (_: Exception) {}
        liveCode = ""
    }

    /** Tes manual dari tombol: paksa ensure + kembalikan status jujur. */
    fun test(c: Context): String {
        return try {
            val code = ensurePair(c)
            ensure(c.applicationContext)
            // beri waktu konek max ~8 dtk
            var i = 0
            while (!connected() && i < 16) {
                try { Thread.sleep(500) } catch (_: Exception) { break }
                i++
            }
            val msg = if (connected()) "Online ✅ kode $code tersambung ke broker."
            else "❌ Belum tersambung (${lastError.ifBlank { "cek internet HP" }}) — tunggu 30 dtk lalu tes lagi."
            Prefs.setLastSync(c, stamp(connected(), msg))
            msg
        } catch (e: Exception) {
            val m = "Error: ${e.message}"
            try { Prefs.setLastSync(c, stamp(false, m)) } catch (_: Exception) {}
            m
        }
    }

    fun ensurePair(c: Context): String {
        var pair = Prefs.getPair(c)
        if (pair.isBlank()) {
            pair = (100000..999999).random().toString()
            Prefs.setPair(c, pair)
        }
        return pair
    }

    private fun ensure(c: Context) {
        val code = ensurePair(c)
        val cur = client
        if (cur != null && liveCode == code) return // biar auto-reconnect yang kerja
        // tutup koneksi lama (mis. habis ganti kode)
        try { try { cur?.disconnectForcibly() } catch (_: Exception) {} } catch (_: Exception) {}
        val aid = Prefs.getAndroidId(c)
        val id = "am-hp-$aid-${(1000..9999).random()}"
        val cl = MqttClient(HOST, id, MemoryPersistence())
        val opt = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 30
            // LWT: bila HP putus tiba-tiba, broker otomatis tulis "offline"
            setWill(statusTopic(code), "offline".toByteArray(), 1, true)
        }
        cl.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, url: String?) {
                try {
                    liveCode = code
                    lastError = ""
                    // cek flag stop persisten setelah (re)connect
                    try { RingManager.checkStopFlag(c) } catch (_: Exception) {}
                    try { FlashManager.checkStopFlag(c) } catch (_: Exception) {}
                    // lahir: tandai online (retained) + subscribe perintah
                    beat(code, cl)
                    cl.subscribe(cmdTopic(code), 1)
                    Prefs.setLastSync(c, stamp(true, "tersambung ke broker"))
                    startBeat(c, code, cl)
                } catch (e: Exception) { lastError = e.message ?: "err" }
            }
            override fun connectionLost(t: Throwable?) {
                lastError = t?.message ?: "putus"
                try { Prefs.setLastSync(c, stamp(false, "koneksi putus")) } catch (_: Exception) {}
            }
            override fun messageArrived(topic: String?, m: MqttMessage?) {
                try { onCmd(c, code, m?.payload?.let { String(it) } ?: "") } catch (_: Exception) {}
            }
            override fun deliveryComplete(t: IMqttDeliveryToken?) {}
        })
        client = cl
        try {
            cl.connect(opt)
        } catch (e: Exception) {
            lastError = e.message ?: "gagal konek"
            // automaticReconnect akan coba lagi sendiri
        }
    }

    /** Publish status online (retained timestamp) — dipanggil tiap konek + tiap 20 dtk. */
    private fun beat(code: String, cl: MqttClient) {
        try {
            cl.publish(statusTopic(code), MqttMessage("${System.currentTimeMillis()}".toByteArray()).apply {
                qos = 1; isRetained = true
            })
        } catch (_: Exception) {}
    }

    private fun startBeat(c: Context, code: String, cl: MqttClient) {
        val g = ++beatGen
        Thread {
            while (g == beatGen) {
                try { Thread.sleep(20000) } catch (_: Exception) { break }
                try {
                    if (g != beatGen) break
                    if (client === cl && cl.isConnected && liveCode == code) beat(code, cl)
                    else break
                } catch (_: Exception) { break }
            }
        }.apply { isDaemon = true; name = "mqtt-beat"; start() }
    }

    private fun onCmd(c: Context, code: String, payload: String) {
        try {
            val j = JSONObject(payload)
            val type = j.optString("type", "")
            val arg = j.optString("arg", "")
            val id = j.optString("id", "")
            if (type.isBlank()) return
            val r = CommandHandler.execCloud(c, type, arg)
            val out = JSONObject()
            out.put("id", id); out.put("cmd", type)
            out.put("kind", if (r.image.isNotEmpty()) "image" else "text")
            out.put("text", r.text.take(2000))
            out.put("ts", System.currentTimeMillis().toString())
            if (r.image.isNotEmpty()) out.put("image", shrinkB64(r.image))
            try {
                client?.publish(resTopic(code), MqttMessage(out.toString().toByteArray()).apply { qos = 1 })
            } catch (_: Exception) {}
            try { Prefs.setLastSync(c, stamp(true, "perintah $type ok")) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    /** Kecilkan base64 JPEG agar muat di 1 pesan MQTT (target <= 100KB). */
    private fun shrinkB64(b64: String): String {
        return try {
            var data = try { Base64.decode(b64, Base64.DEFAULT) } catch (_: Exception) { return "" }
            if (data.size <= 100_000) return Base64.encodeToString(data, Base64.NO_WRAP)
            var bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return ""
            var scale = 0.7f
            var q = 45
            repeat(3) {
                val w = (bmp.width * scale).toInt().coerceAtLeast(160)
                val h = (bmp.height * scale).toInt().coerceAtLeast(120)
                val s = Bitmap.createScaledBitmap(bmp, w, h, true)
                try { if (s != bmp) bmp.recycle() } catch (_: Exception) {}
                bmp = s
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
                data = out.toByteArray()
                if (data.size <= 100_000) return Base64.encodeToString(data, Base64.NO_WRAP)
                scale *= 0.7f; q = (q - 10).coerceAtLeast(25)
            }
            try { bmp.recycle() } catch (_: Exception) {}
            Base64.encodeToString(data, Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }

    private fun stamp(ok: Boolean, detail: String): String {
        return try {
            val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            (if (ok) "✅ " else "❌ ") + t + " " + detail
        } catch (_: Exception) { detail }
    }
}
