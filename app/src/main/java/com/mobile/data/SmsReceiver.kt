package com.mobile.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Unhandled exception in SmsReceiver coroutine", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = try { intent.action } catch (_: Throwable) { null }
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val appContext = context.applicationContext
        val pendingResult = try { goAsync() } catch (_: Throwable) { null }

        scope.launch {
            try {
                SettingsRepository.init(appContext)
                FinanceRepository.init(appContext)

                val messages = extractMessages(intent)
                if (messages.isNotEmpty()) {
                    val fullBody = StringBuilder()
                    var originatingAddress: String? = null
                    var timestamp = 0L

                    for (sms in messages) {
                        try {
                            if (originatingAddress == null) originatingAddress = sms.displayOriginatingAddress
                            if (timestamp == 0L) timestamp = sms.timestampMillis
                            val bodyPart = sms.displayMessageBody
                            if (bodyPart != null) {
                                fullBody.append(bodyPart)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error reading message part", t)
                        }
                    }

                    val address = originatingAddress
                    val body = fullBody.toString()

                    if (!address.isNullOrBlank() && body.isNotBlank()) {
                        val parsedTx = SmsParser.parseMessage(address, body, timestamp)
                        if (parsedTx != null) {
                            FinanceRepository.addTransaction(parsedTx)
                            try {
                                TransactionNotifier.notify(appContext, parsedTx)
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error posting transaction notification", t)
                            }
                            try {
                                WeeklySpendingWidgetUpdater.updateAllWidgets(appContext)
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error updating widgets from SMS", t)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in SmsReceiver processing", t)
            } finally {
                if (pendingResult != null) {
                    runCatching { pendingResult.finish() }
                }
            }
        }
    }

    /**
     * Safely extracts SMS messages handling Samsung / OEM dual-SIM quirks where intent extras
     * contain ArrayList instead of Object[], or proprietary SMS extras.
     */
    private fun extractMessages(intent: Intent): List<SmsMessage> {
        return try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        } catch (_: Throwable) {
            // Samsung dual-SIM fallback
            try {
                val extras = intent.extras ?: return emptyList()
                val pdusObj = extras.get("pdus") ?: return emptyList()
                val pdus: Array<*> = when (pdusObj) {
                    is Array<*> -> pdusObj
                    is java.util.ArrayList<*> -> pdusObj.toArray()
                    is List<*> -> pdusObj.toTypedArray()
                    else -> return emptyList()
                }
                val format = extras.getString("format")
                pdus.mapNotNull { pdu ->
                    val bytes = pdu as? ByteArray ?: return@mapNotNull null
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            SmsMessage.createFromPdu(bytes, format)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsMessage.createFromPdu(bytes)
                        }
                    } catch (_: Throwable) {
                        null
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Fallback SMS message extraction failed", t)
                emptyList()
            }
        }
    }
}
