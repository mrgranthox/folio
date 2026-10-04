package com.example.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.data.repository.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages: Array<SmsMessage>? = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages != null && messages.isNotEmpty()) {
                val fullBody = StringBuilder()
                var sender = "Unknown"
                for (sms in messages) {
                    sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: "Unknown"
                    fullBody.append(sms.displayMessageBody ?: sms.messageBody ?: "")
                }
                val body = fullBody.toString()

                val repo = ExpenseRepository(context.applicationContext)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        repo.ingestSms(sender, body)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
