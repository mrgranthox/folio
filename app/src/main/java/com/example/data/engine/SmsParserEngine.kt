package com.example.data.engine

import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

data class ParsedSmsResult(
    val isFinancial: Boolean,
    val externalRef: String? = null,
    val amount: Double? = null,
    val currency: String = "GHS",
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val counterparty: String = "Unknown Payee",
    val endingBalance: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val rawBody: String,
    val provider: String
) {
    fun toTransactionEntity(
        accountId: String,
        accountRail: String? = null,
        categoryId: String = "cat-food",
        categoryName: String? = "Food & Dining",
        categoryColor: String? = "#F59E0B"
    ): TransactionEntity {
        val amt = amount ?: 0.0
        val finalAmount = if (direction == TransactionDirection.DEBIT) -kotlin.math.abs(amt) else kotlin.math.abs(amt)
        val type = if (direction == TransactionDirection.CREDIT) "Income" else "Expense"

        return TransactionEntity(
            id = UUID.randomUUID().toString(),
            externalRef = externalRef,
            accountId = accountId,
            categoryId = categoryId,
            amount = finalAmount,
            currency = currency,
            direction = direction.value,
            type = type,
            timestamp = timestamp,
            counterparty = counterparty,
            sourceMethod = "sms",
            notes = "Automated ingest via $provider",
            isVerified = true,
            isDeleted = false,
            createdAt = System.currentTimeMillis(),
            categoryName = categoryName,
            categoryColor = categoryColor,
            accountRail = accountRail ?: provider
        )
    }
}

class SmsParserEngine {

    fun parse(sender: String, body: String): ParsedSmsResult {
        val cleanBody = body.trim()
        val lowerBody = cleanBody.lowercase(Locale.ROOT)
        val lowerSender = sender.lowercase(Locale.ROOT)

        val isMoMo = lowerSender.contains("momo") ||
                lowerSender.contains("mtn") ||
                lowerBody.contains("mobilemoney") ||
                lowerBody.contains("momo") ||
                lowerBody.contains("financial transaction id")

        val isTelecel = lowerSender.contains("telecel") ||
                lowerSender.contains("vodafone") ||
                lowerBody.contains("telecel cash") ||
                lowerBody.contains("v-cash")

        val isAt = lowerSender.contains("airteltigo") ||
                lowerSender.contains("at money") ||
                lowerBody.contains("at money")

        val isBank = lowerSender.contains("stanbic") ||
                lowerSender.contains("ecobank") ||
                lowerSender.contains("zenith") ||
                lowerSender.contains("gtbank") ||
                lowerSender.contains("absa") ||
                lowerSender.contains("gcb") ||
                lowerSender.contains("fidelity") ||
                lowerSender.contains("calbank") ||
                lowerSender.contains("access") ||
                lowerBody.contains("acct") ||
                lowerBody.contains("pos txn")

        // 1. Extract External Reference ID (Prioritize official Financial Transaction ID)
        var externalRef: String? = null
        val financialTxnPattern = Pattern.compile(
            """(?:Financial\s*Transaction\s*Id|Txn\s*ID|TxnId|Transaction\s*ID)\s*[:.]?\s*([A-Za-z0-9\-_]{6,30})""",
            Pattern.CASE_INSENSITIVE
        )
        val financialTxnMatcher = financialTxnPattern.matcher(cleanBody)
        if (financialTxnMatcher.find()) {
            externalRef = financialTxnMatcher.group(1)?.trim()
        } else {
            val genericRefPattern = Pattern.compile(
                """(?:Ref\s*ID|Reference|Ref\.?|ID[:.]?)\s*[:.]?\s*([A-Za-z0-9\-_]{6,30})""",
                Pattern.CASE_INSENSITIVE
            )
            val genericRefMatcher = genericRefPattern.matcher(cleanBody)
            if (genericRefMatcher.find()) {
                externalRef = genericRefMatcher.group(1)?.trim()
            }
        }

        // 2. Extract Currency
        var currency = "GHS"
        if (cleanBody.contains("USD") || cleanBody.contains("$")) {
            currency = "USD"
        } else if (cleanBody.contains("EUR") || cleanBody.contains("€")) {
            currency = "EUR"
        } else if (cleanBody.contains("GBP") || cleanBody.contains("£")) {
            currency = "GBP"
        }

        // 3. Extract Amount
        var amount: Double? = null
        val amountPattern = Pattern.compile(
            """(?:GHS|GH[Cc¢₵\u20B5\u01B5]|USD|\$|EUR|€|GBP|£|amount\s*of|paid|received|sent|debited|credited|for)\s*([0-9,]+(?:\.[0-9]{2})?)""",
            Pattern.CASE_INSENSITIVE
        )
        val amountMatcher = amountPattern.matcher(cleanBody)
        if (amountMatcher.find()) {
            val amtStr = amountMatcher.group(1)?.replace(",", "")
            amount = amtStr?.toDoubleOrNull()
        } else {
            val fallbackPattern = Pattern.compile("""\b([0-9]{1,6}\.[0-9]{2})\b""")
            val fallbackMatcher = fallbackPattern.matcher(cleanBody)
            if (fallbackMatcher.find()) {
                amount = fallbackMatcher.group(1)?.toDoubleOrNull()
            }
        }

        // 4. Extract Direction (Debit vs Credit)
        var direction = TransactionDirection.DEBIT
        if (lowerBody.contains("received") ||
            lowerBody.contains("credited") ||
            lowerBody.contains("cash in") ||
            lowerBody.contains("deposit") ||
            lowerBody.contains("you have received") ||
            lowerBody.contains("received payment")
        ) {
            direction = TransactionDirection.CREDIT
        }

        // 5. Extract Counterparty / Merchant
        var counterparty: String? = null

        // Priority 5a: Specific utility / power / water meter pattern: e.g. "to meter 54310900789 (STEPHEN ETSE)"
        val meterPattern = Pattern.compile(
            """(?:to\s+meter|meter)\s+([A-Za-z0-9\-]+)\s*(?:\(([^)]+)\))?""",
            Pattern.CASE_INSENSITIVE
        )
        val meterMatcher = meterPattern.matcher(cleanBody)
        if (meterMatcher.find()) {
            val meterNum = meterMatcher.group(1)?.trim()
            val name = meterMatcher.group(2)?.trim()
            if (!name.isNullOrBlank()) {
                counterparty = "$name (Meter $meterNum)"
            } else if (!meterNum.isNullOrBlank()) {
                counterparty = "ECG Meter $meterNum"
            }
        }

        // Priority 5b: General merchant pattern
        if (counterparty == null) {
            val merchantPattern = Pattern.compile(
                """(?:payment to|paid to|sent to|made to|transferred to|received from|from|\bto)\s+([A-Za-z0-9\s\-()&'.]+?)(?:\.\s*(?:Current|Bal|Ref|Txn|Fee|is)|\s+has\s+been|\s+on\s+\d|\s*$|\.)""",
                Pattern.CASE_INSENSITIVE
            )
            val merchantMatcher = merchantPattern.matcher(cleanBody)
            while (merchantMatcher.find()) {
                val candidate = merchantMatcher.group(1)?.trim()
                val lowerCandidate = candidate?.lowercase(Locale.ROOT) ?: ""
                val isActionPhrase = lowerCandidate.startsWith("recharge your meter") ||
                        lowerCandidate.startsWith("recharge meter") ||
                        lowerCandidate.startsWith("use this token") ||
                        lowerCandidate.startsWith("buy bundle") ||
                        lowerCandidate.startsWith("pay bill") ||
                        lowerCandidate.startsWith("cash out") ||
                        lowerCandidate.startsWith("cash in")
                if (!candidate.isNullOrBlank() && candidate.length in 2..60 && !isActionPhrase) {
                    counterparty = candidate
                    break
                }
            }
        }

        // 6. Extract Ending Balance
        var endingBalance: Double? = null
        val balPattern = Pattern.compile(
            """(?:Current\s*Balance|Bal[:.]?|Balance[:.]?|Available\s*Balance)\s*(?:is\s*)?(?:GHS|GH[Cc¢₵\u20B5\u01B5]|USD|\$)?\s*([0-9,]+(?:\.[0-9]{2})?)""",
            Pattern.CASE_INSENSITIVE
        )
        val balMatcher = balPattern.matcher(cleanBody)
        if (balMatcher.find()) {
            val balStr = balMatcher.group(1)?.replace(",", "")
            endingBalance = balStr?.toDoubleOrNull()
        }

        // 7. Extract Date
        var timestamp = System.currentTimeMillis()
        val datePattern = Pattern.compile("""\bon\s+(\d{1,2})[-/.](\d{1,2})[-/.](20\d{2}|\d{2})\b""")
        val dateMatcher = datePattern.matcher(cleanBody)
        if (dateMatcher.find()) {
            try {
                val d = dateMatcher.group(1)!!.toInt()
                val m = dateMatcher.group(2)!!.toInt() - 1
                var y = dateMatcher.group(3)!!.toInt()
                if (y < 100) y += 2000
                val cal = Calendar.getInstance()
                cal.set(y, m, d)
                timestamp = cal.timeInMillis
            } catch (_: Exception) {}
        }

        val provider = when {
            isMoMo -> "MTN MoMo"
            isTelecel -> "Telecel Cash"
            isAt -> "AT Money"
            isBank -> "Bank Alert"
            else -> "SMS Ingestion"
        }

        val resolvedCounterparty = counterparty
            ?: if (direction == TransactionDirection.CREDIT) "Payment Received" else "Payment Disbursed"

        return ParsedSmsResult(
            isFinancial = amount != null && amount > 0,
            externalRef = externalRef,
            amount = amount,
            currency = currency,
            direction = direction,
            counterparty = resolvedCounterparty,
            endingBalance = endingBalance,
            timestamp = timestamp,
            rawBody = cleanBody,
            provider = provider
        )
    }
}
