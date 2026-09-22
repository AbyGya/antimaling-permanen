package com.antimaling.permanen.control

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.antimaling.permanen.util.Prefs

object RingManager {
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var vibing = false
    @Volatile private var gen = 0

    fun start(c: Context) {
        try {
            Prefs.setRinging(c, true)
            maxVolume(c)
            stopPlayer()
            val g = ++gen
            val uri = try { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) }
            catch (_: Exception) { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) }
            player = try {
                MediaPlayer().apply {
                    setDataSource(c, uri)
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                try {
                    MediaPlayer.create(c, uri)?.apply { isLooping = true; start() }
                } catch (_: Exception) { null }
            }
            startVibrate(c, g)
        } catch (_: Exception) {}
    }

    fun stop(c: Context) {
        try {
            gen++ // bunuh SEMUA thread getar dari start() manapun (anti race)
            Prefs.setRinging(c, false)
            vibing = false
            stopPlayer()
            try { Thread.sleep(50) } catch (_: Exception) {}
            stopPlayer()
            try {
                val v = vibrator(c)
                v?.cancel()
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private fun stopPlayer() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    private fun maxVolume(c: Context) {
        try {
            val am = c.getSystemService(AudioManager::class.java) ?: return
            for (s in listOf(AudioManager.STREAM_ALARM, AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING)) {
                try {
                    val max = am.getStreamMaxVolume(s)
                    am.setStreamVolume(s, max, 0)
                } catch (_: Exception) {}
            }
            try { am.mode = AudioManager.MODE_NORMAL } catch (_: Exception) {}
            try { am.isSpeakerphoneOn = true } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private fun vibrator(c: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) c.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } catch (_: Exception) { null }

    private fun startVibrate(c: Context, g: Int) {
        try {
            vibing = true
            val v = vibrator(c) ?: return
            if (!v.hasVibrator()) return
            Thread {
                try {
                    while (vibing && g == gen) {
                        try {
                            if (Build.VERSION.SDK_INT >= 26)
                                v.vibrate(VibrationEffect.createOneShot(900, VibrationEffect.DEFAULT_AMPLITUDE))
                            else @Suppress("DEPRECATION") v.vibrate(900)
                        } catch (_: Exception) { break }
                        try { Thread.sleep(1200) } catch (_: Exception) { break }
                    }
                } catch (_: Exception) {}
            }.apply { isDaemon = true }.start()
        } catch (_: Exception) {}
    }
}
