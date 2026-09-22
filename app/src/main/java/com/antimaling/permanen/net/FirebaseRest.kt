package com.antimaling.permanen.net

import android.content.Context
import com.antimaling.permanen.util.Prefs
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Firebase via REST murni (HttpURLConnection + org.json bawaan Android).
 * Tanpa SDK tambahan -> build ringan, minim FC.
 * Struktur:
 *   devices/{aid}            : { lastSeen, app, pair }
 *   devices/{aid}/inbox/{id} : { type, arg, ts, from, status }
 *   devices/{aid}/outbox/{id}: { kind, cmd, text, image, ts }
 */
object FirebaseRest {
    private const val T_O = 15000

    // ---------- Auth anonim ----------
    fun ensureAuth(c: Context): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val tok = Prefs.getIdToken(c)
            if (tok.isNotBlank() && now < Prefs.getTokenExp(c) - 60_000) return true
            val ref = Prefs.getRefresh(c)
            if (ref.isNotBlank() && refresh(c, ref)) return true
            signUp(c)
        } catch (_: Exception) { false }
    }

    private fun key(c: Context) = Prefs.getFbKey(c)
    private fun proj(c: Context) = Prefs.getFbProject(c)

    private fun signUp(c: Context): Boolean {
        return try {
            val r = postJson(
                "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${key(c)}",
                JSONObject().put("returnSecureToken", true).toString(), null
            ) ?: return false
            saveTokens(c, r)
        } catch (_: Exception) { false }
    }

    private fun refresh(c: Context, ref: String): Boolean {
        return try {
            val body = "grant_type=refresh_token&refresh_token=${enc(ref)}"
            val r = postForm(
                "https://securetoken.googleapis.com/v1/token?key=${key(c)}", body
            ) ?: return false
            Prefs.setIdToken(c, r.optString("id_token", ""))
            Prefs.setRefresh(c, r.optString("refresh_token", ref))
            val exp = (r.optString("expires_in", "3600").toLongOrNull() ?: 3600) * 1000
            Prefs.setTokenExp(c, System.currentTimeMillis() + exp)
            Prefs.getIdToken(c).isNotBlank()
        } catch (_: Exception) { false }
    }

    private fun saveTokens(c: Context, r: JSONObject): Boolean {
        return try {
            Prefs.setIdToken(c, r.optString("idToken", ""))
            Prefs.setRefresh(c, r.optString("refreshToken", ""))
            val exp = (r.optString("expiresIn", "3600").toLongOrNull() ?: 3600) * 1000
            Prefs.setTokenExp(c, System.currentTimeMillis() + exp)
            Prefs.getIdToken(c).isNotBlank()
        } catch (_: Exception) { false }
    }

    // ---------- Firestore ----------
    private fun base(c: Context) =
        "https://firestore.googleapis.com/v1/projects/${proj(c)}/databases/(default)/documents"

    fun docPath(c: Context, aid: String) = "devices/$aid"

    fun heartbeat(c: Context, aid: String) {
        try {
            patchFields(c, docPath(c, aid), mapOf(
                "lastSeen" to now(), "app" to "1.0",
                "pair" to Prefs.getPair(c), "model" to android.os.Build.MODEL
            ))
            // pointer agar panel laptop bisa menemukan HP dari kode pairing
            val pair = Prefs.getPair(c)
            if (pair.isNotBlank()) patchFields(c, "devices/pair_$pair", mapOf("aid" to aid))
        } catch (_: Exception) {}
    }

    fun listInbox(c: Context, aid: String): List<InboxCmd> {
        val out = mutableListOf<InboxCmd>()
        try {
            val r = get("${base(c)}/${docPath(c, aid)}/inbox?pageSize=25", c) ?: return out
            val docs = r.optJSONArray("documents") ?: return out
            for (i in 0 until docs.length()) {
                try {
                    val d = docs.getJSONObject(i)
                    val f = d.optJSONObject("fields") ?: continue
                    if (s(f, "status") != "pending") continue
                    out.add(InboxCmd(
                        name = d.optString("name", ""),
                        type = s(f, "type"), arg = s(f, "arg"),
                        ts = s(f, "ts"), from = s(f, "from")
                    ))
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return out
    }

    fun deleteDoc(c: Context, fullName: String) {
        try {
            val u = URL("https://firestore.googleapis.com/v1/$fullName")
            val con = (u.openConnection() as HttpURLConnection)
            con.requestMethod = "DELETE"
            con.setRequestProperty("Authorization", "Bearer ${Prefs.getIdToken(c)}")
            con.connectTimeout = T_O; con.readTimeout = T_O
            try { con.responseCode } catch (_: Exception) {}
            try { con.disconnect() } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    fun sendResult(c: Context, aid: String, kind: String, cmd: String, text: String, imageB64: String = "") {
        try {
            val fields = JSONObject()
            fields.put("kind", sv(kind)); fields.put("cmd", sv(cmd))
            fields.put("text", sv(text.take(2000))); fields.put("ts", sv(now()))
            if (imageB64.isNotEmpty()) fields.put("image", sv(imageB64))
            postJson("${base(c)}/${docPath(c, aid)}/outbox", JSONObject().put("fields", fields).toString(), c)
            pruneOutbox(c, aid)
        } catch (_: Exception) {}
    }

    private fun pruneOutbox(c: Context, aid: String) {
        // tahan max ~20 hasil terakhir agar DB tidak membengkak
        try {
            val r = get("${base(c)}/${docPath(c, aid)}/outbox?pageSize=50&orderBy=ts", c) ?: return
            val docs = r.optJSONArray("documents") ?: return
            if (docs.length() <= 20) return
            for (i in 0 until docs.length() - 20) {
                try { deleteDoc(c, docs.getJSONObject(i).optString("name", "")) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun patchFields(c: Context, doc: String, fields: Map<String, String>) {
        try {
            val f = JSONObject()
            fields.forEach { (k, v) -> f.put(k, sv(v)) }
            var url = "${base(c)}/$doc?"
            fields.keys.forEach { url += "updateMask.fieldPaths=$it&" }
            val u = URL(url)
            val con = (u.openConnection() as HttpURLConnection)
            con.requestMethod = "PATCH"
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json")
            con.setRequestProperty("Authorization", "Bearer ${Prefs.getIdToken(c)}")
            con.connectTimeout = T_O; con.readTimeout = T_O
            OutputStreamWriter(con.outputStream).use { it.write(JSONObject().put("fields", f).toString()) }
            try { con.inputStream.close() } catch (_: Exception) { try { con.errorStream?.close() } catch (_: Exception) {} }
            try { con.disconnect() } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    // ---------- HTTP helpers ----------
    private fun auth(c: Context?) = if (c == null) null else "Bearer ${Prefs.getIdToken(c)}"

    fun get(url: String, c: Context): JSONObject? {
        return try {
            val con = (URL(url).openConnection() as HttpURLConnection)
            con.requestMethod = "GET"
            con.setRequestProperty("Authorization", auth(c))
            con.connectTimeout = T_O; con.readTimeout = T_O
            read(con)
        } catch (_: Exception) { null }
    }

    private fun postJson(url: String, body: String, c: Context?): JSONObject? {
        return try {
            val con = (URL(url).openConnection() as HttpURLConnection)
            con.requestMethod = "POST"
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json")
            if (c != null) con.setRequestProperty("Authorization", auth(c))
            con.connectTimeout = T_O; con.readTimeout = T_O
            OutputStreamWriter(con.outputStream).use { it.write(body) }
            read(con)
        } catch (_: Exception) { null }
    }

    private fun postForm(url: String, body: String): JSONObject? {
        return try {
            val con = (URL(url).openConnection() as HttpURLConnection)
            con.requestMethod = "POST"
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            con.connectTimeout = T_O; con.readTimeout = T_O
            OutputStreamWriter(con.outputStream).use { it.write(body) }
            read(con)
        } catch (_: Exception) { null }
    }

    private fun read(con: HttpURLConnection): JSONObject? {
        return try {
            val code = con.responseCode
            val s = try {
                con.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                try { con.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { "" } ?: ""
            }
            try { con.disconnect() } catch (_: Exception) {}
            if (code in 200..299) JSONObject(if (s.isBlank()) "{}" else s) else null
        } catch (_: Exception) { null }
    }

    private fun sv(v: String) = JSONObject().put("stringValue", v)
    private fun s(f: JSONObject, k: String): String {
        return try { f.optJSONObject(k)?.optString("stringValue", "") ?: "" } catch (_: Exception) { "" }
    }
    private fun now() = System.currentTimeMillis().toString()
    private fun enc(s: String) = try { java.net.URLEncoder.encode(s, "UTF-8") } catch (_: Exception) { s }

    data class InboxCmd(val name: String, val type: String, val arg: String, val ts: String, val from: String)
}
