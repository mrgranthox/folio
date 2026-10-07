package com.example.data.sms

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.example.data.engine.ReconciliationOutcome
import com.example.data.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmsSyncReport(
    val totalScanned: Int,
    val financialCount: Int,
    val insertedCount: Int,
    val duplicateSkippedCount: Int,
    val autoMergedCount: Int,
    val queuedForReviewCount: Int
)

class SmsInboxReader(private val context: Context) {

    suspend fun scanAndIngestInbox(
        repository: ExpenseRepository,
        maxMessages: Int = 100
    ): SmsSyncReport = withContext(Dispatchers.IO) {
        var totalScanned = 0
        var financialCount = 0
        var insertedCount = 0
        var duplicateSkippedCount = 0
        var autoMergedCount = 0
        var queuedCount = 0

        try {
            val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms.Inbox.ADDRESS,
                Telephony.Sms.Inbox.BODY,
                Telephony.Sms.Inbox.DATE
            )

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Sms.Inbox.DATE} DESC LIMIT $maxMessages"
            )

            cursor?.use { c ->
                val addressIdx = c.getColumnIndex(Telephony.Sms.Inbox.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.Inbox.BODY)

                while (c.moveToNext()) {
                    totalScanned++
                    val sender = if (addressIdx != -1) c.getString(addressIdx) ?: "Unknown" else "Unknown"
                    val body = if (bodyIdx != -1) c.getString(bodyIdx) ?: "" else ""

                    if (body.isNotBlank()) {
                        val (outcome, _) = repository.ingestSms(sender, body)
                        when (outcome) {
                            ReconciliationOutcome.INSERTED_NEW -> {
                                financialCount++
                                insertedCount++
                            }
                            ReconciliationOutcome.IDEMPOTENT_SKIP -> {
                                financialCount++
                                duplicateSkippedCount++
                            }
                            ReconciliationOutcome.AUTO_MERGED -> {
                                financialCount++
                                autoMergedCount++
                            }
                            ReconciliationOutcome.QUEUED_FOR_REVIEW -> {
                                financialCount++
                                queuedCount++
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        SmsSyncReport(
            totalScanned = totalScanned,
            financialCount = financialCount,
            insertedCount = insertedCount,
            duplicateSkippedCount = duplicateSkippedCount,
            autoMergedCount = autoMergedCount,
            queuedForReviewCount = queuedCount
        )
    }
}
