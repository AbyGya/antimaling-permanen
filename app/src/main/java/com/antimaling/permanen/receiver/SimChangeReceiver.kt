package com.antimaling.permanen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.antimaling.permanen.control.CommandHandler
import com.antimaling.permanen.util.Prefs

class SimChangeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        try {
            val trusted = Prefs.getTrusted(c)
            if (trusted.isBlank()) return
            val cur = try {
                val tm = c.getSystemService(TelephonyManager::class.java) ?: return
                (try { tm.simOperator } catch (_: Exception) { "" }) + "|" +
                        (try { tm.simCountryIso } catch (_: Exception) { "" }) + "|" +
                        (try { tm.networkOperatorName } catch (_: Exception) { "" })
            } catch (_: SecurityException) { return }
            catch (_: Exception) { return }
            val old = Prefs.getSim(c)
            if (old.isBlank()) { Prefs.setSim(c, cur); return }
            if (old != cur) {
                Prefs.setSim(c, cur)
                CommandHandler.reply(c, trusted, "AntiMaling: SIM BERUBAH! HP mungkin dicuri. Lokasi: kirim #LOCATE. Untuk kunci kirim #LOCK.")
            }
        } catch (_: Exception) {}
    }
}
