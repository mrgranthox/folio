package com.example.data.engine

import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

enum class SmsTransactionSubtype {
    MERCHANT_PAYMENT,
    P2P_SENT,
    P2P_RECEIVED,
    CASH_OUT,
    CASH_IN,
    BILL_RECHARGE,
    AIRTIME_BUNDLE,
    BANK_DEBIT,
    BANK_CREDIT,
    ATM_WITHDRAWAL,
    TRANSFER,
    GENERAL_EXPENSE,
    GENERAL_INCOME
}

data class ParsedSmsResult(
    val isFinancial: Boolean,
    val isPromotional: Boolean = false,
    val rejectionReason: String? = null,
    val externalRef: String? = null,
    val amount: Double? = null,
    val fee: Double? = null,
    val currency: String = "GHS",
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val subType: SmsTransactionSubtype = SmsTransactionSubtype.GENERAL_EXPENSE,
    val counterparty: String = "Unknown Payee",
    val counterpartyPhone: String? = null,
    val meterNumber: String? = null,
    val rechargeToken: String? = null,
    val endingBalance: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val rawBody: String,
    val provider: String,
    val suggestedCategory: String? = null,
    val confidenceScore: Double = 0.95,
    val isAiParsed: Boolean = false
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
        val type = when (direction) {
            TransactionDirection.CREDIT -> "Income"
            else -> when (subType) {
                SmsTransactionSubtype.BILL_RECHARGE -> "Bill"
                SmsTransactionSubtype.P2P_SENT, SmsTransactionSubtype.TRANSFER -> "Transfer"
                else -> "Expense"
            }
        }

        val noteParts = mutableListOf<String>()
        noteParts.add("Automated ingest via $provider")
        if (!rechargeToken.isNullOrBlank()) {
            noteParts.add("Token: $rechargeToken")
        }
        if (fee != null && fee > 0) {
            noteParts.add("Fee: $currency ${String.format(Locale.US, "%.2f", fee)}")
        }
        if (!meterNumber.isNullOrBlank()) {
            noteParts.add("Meter: $meterNumber")
        }

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
            notes = noteParts.joinToString(" • "),
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

    companion object {
        // Recognized Ghanaian financial & telecom rails
        val AUTHORIZED_MOMO_SENDERS = listOf(
            "mobilemoney", "momo", "mtn", "170", "mtn momo", "mtnmomo"
        )
        val AUTHORIZED_TELECEL_SENDERS = listOf(
            "telecel", "vodafone", "t-cash", "tcash", "v-cash", "vcash", "telecel cash", "telecelcash"
        )
        val AUTHORIZED_AT_SENDERS = listOf(
            "airteltigo", "at money", "atmoney", "airteltigomoney"
        )
        val AUTHORIZED_BANK_SENDERS = listOf(
            "ecobank", "stanbic", "gcb", "gtbank", "zenith", "absa", "fidelity",
            "calbank", "access", "standard chartered", "stanchart", "fnb", "uba",
            "republic", "cbg", "omnibsic", "prudential", "first atlantic", "boa", "bank"
        )
        val AUTHORIZED_UTILITY_SENDERS = listOf(
            "ecg", "gwcl", "zeepay", "g-money", "gmoney"
        )

        val PROMOTIONAL_OR_MANAGEMENT_KEYWORDS = listOf(
            // Commercial promotions / lotteries / offers
            "win", "winner", "promo", "promotion", "bonus", "double bonus", "mashup",
            "dial *", "dial #", "subscribe", "subscribing", "subscription",
            "stand a chance", "congratulations", "lucky", "cashback", "reward points", "loyalty points",
            "free gb", "data bundle offer", "buy bundle", "bundle offer", "special offer",
            "discount", "coupon", "voucher", "enjoy up to", "get up to", "airtime offer",
            "spin & win", "spin and win", "raffle", "jackpot", "play now",

            // Solicitations, loans, certificates, billing reminders (money hasn't actually moved)
            "apply for", "apply now", "qualify for", "get a loan", "quick loan", "loan offer",
            "borrow up to", "pay only", "out of for", "certificate", "cost is",
            "charges apply", "admission", "tuition fee", "fee is",
            "your loan balance", "repay your loan", "repayment is due",

            // Management, Security notices, OTPs, Systems
            "terms and conditions", "terms & conditions", "t&cs apply", "please note that",
            "scheduled maintenance", "system upgrade", "service interruption",
            "scam alert", "fraud alert", "do not share your pin", "never share your pin",
            "otp", "one-time password", "verification code", "secret code",
            "pin reset", "sim registration", "dear customer",
            "welcome to mtn", "welcome to telecel", "welcome to ecobank"
        )
    }

    fun isAuthorizedFinancialSender(sender: String, body: String): Boolean {
        val s = sender.lowercase(Locale.ROOT).trim()
        val b = body.lowercase(Locale.ROOT)

        if (AUTHORIZED_MOMO_SENDERS.any { s.contains(it) }) return true
        if (AUTHORIZED_TELECEL_SENDERS.any { s.contains(it) }) return true
        if (AUTHORIZED_AT_SENDERS.any { s.contains(it) }) return true
        if (AUTHORIZED_BANK_SENDERS.any { s.contains(it) }) return true
        if (AUTHORIZED_UTILITY_SENDERS.any { s.contains(it) }) return true

        // If sender is unknown/empty/generic, check for unambiguous cryptographic/financial header in body:
        val hasExplicitMoMoHeader = b.contains("financial transaction id") ||
                b.contains("mobilemoney") ||
                b.contains("telecel cash balance") ||
                b.contains("at money balance") ||
                (b.contains("payment made for") && b.contains("current balance")) ||
                (b.contains("cash out of") && b.contains("agent")) ||
                (b.contains("cash in received for") && b.contains("reference:"))
        val hasExplicitBankHeader = (b.contains("acct:") || b.contains("acct **") || b.contains("acct no")) &&
                (b.contains("avail bal") || b.contains("bal:") || b.contains("balance:") || b.contains("pos purchase") || b.contains("atm wdl"))

        return hasExplicitMoMoHeader || hasExplicitBankHeader
    }

    fun isPromotionalOrManagementMessage(body: String, sender: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)
        val lowerSender = sender.lowercase(Locale.ROOT)

        if (lowerSender.contains("promo") || lowerSender.contains("offer") || lowerSender.contains("marketing") || lowerSender.contains("ad")) {
            return true
        }

        return PROMOTIONAL_OR_MANAGEMENT_KEYWORDS.any { lower.contains(it) }
    }

    fun hasVerifiableTransactionProof(
        cleanBody: String,
        lowerBody: String,
        externalRef: String?,
        endingBalance: Double?,
        subType: SmsTransactionSubtype
    ): Boolean {
        // Proof 1: Official Financial Transaction ID / Reference (5+ chars)
        if (!externalRef.isNullOrBlank() && externalRef.length >= 5) {
            return true
        }

        // Proof 2: Ending Balance Audit Statement
        if (endingBalance != null && endingBalance >= 0) {
            return true
        }

        // Proof 3: Definite Past-Tense Execution Grammar from Official Financial Entity
        val hasDefiniteExecutionVerb = lowerBody.contains("payment made for") ||
                lowerBody.contains("payment of") ||
                lowerBody.contains("you have received") ||
                lowerBody.contains("cash out of") ||
                lowerBody.contains("cash in received") ||
                lowerBody.contains("cash in of") ||
                lowerBody.contains("you have transferred") ||
                lowerBody.contains("transferred ghs") ||
                lowerBody.contains("transfer of") ||
                lowerBody.contains("debit alert") ||
                lowerBody.contains("credit alert") ||
                lowerBody.contains("was debited with") ||
                lowerBody.contains("was credited with") ||
                lowerBody.contains("pos purchase") ||
                lowerBody.contains("atm wdl") ||
                lowerBody.contains("use this token") ||
                lowerBody.contains("recharge token")

        return hasDefiniteExecutionVerb
    }

    fun parse(sender: String, body: String): ParsedSmsResult {
        val cleanBody = body.trim()
        val lowerBody = cleanBody.lowercase(Locale.ROOT)
        val lowerSender = sender.lowercase(Locale.ROOT)

        // -------------------------------------------------------------
        // 1. Identify Financial Provider / Channel
        // -------------------------------------------------------------
        val isMoMo = lowerSender.contains("momo") ||
                lowerSender.contains("mtn") ||
                lowerSender.contains("mobilemoney") ||
                lowerBody.contains("mobilemoney") ||
                lowerBody.contains("momo") ||
                lowerBody.contains("financial transaction id")

        val isTelecel = lowerSender.contains("telecel") ||
                lowerSender.contains("vodafone") ||
                lowerSender.contains("t-cash") ||
                lowerSender.contains("tcash") ||
                lowerSender.contains("v-cash") ||
                lowerBody.contains("telecel cash") ||
                lowerBody.contains("t-cash") ||
                lowerBody.contains("v-cash")

        val isAt = lowerSender.contains("airteltigo") ||
                lowerSender.contains("at money") ||
                lowerSender.contains("atmoney") ||
                lowerBody.contains("at money") ||
                lowerBody.contains("atmoney")

        val isZeepay = lowerSender.contains("zeepay") || lowerBody.contains("zeepay")
        val isGMoney = lowerSender.contains("g-money") || lowerSender.contains("gmoney") || lowerBody.contains("g-money")

        val isBank = lowerSender.contains("stanbic") ||
                lowerSender.contains("ecobank") ||
                lowerSender.contains("zenith") ||
                lowerSender.contains("gtbank") ||
                lowerSender.contains("absa") ||
                lowerSender.contains("gcb") ||
                lowerSender.contains("fidelity") ||
                lowerSender.contains("calbank") ||
                lowerSender.contains("access") ||
                lowerSender.contains("standard chartered") ||
                lowerSender.contains("fnb") ||
                lowerBody.contains("acct:") ||
                lowerBody.contains("acct **") ||
                lowerBody.contains("pos purchase") ||
                lowerBody.contains("atm wdl")

        val isUtility = lowerSender.contains("ecg") ||
                lowerSender.contains("gwcl") ||
                lowerBody.contains("recharge your meter") ||
                lowerBody.contains("use this token") ||
                lowerBody.contains("meter")

        val provider = when {
            isMoMo -> "MTN MoMo"
            isTelecel -> "Telecel Cash"
            isAt -> "AT Money"
            isZeepay -> "Zeepay"
            isGMoney -> "G-Money"
            isBank -> when {
                lowerSender.contains("ecobank") || lowerBody.contains("ecobank") -> "Ecobank"
                lowerSender.contains("stanbic") || lowerBody.contains("stanbic") -> "Stanbic Bank"
                lowerSender.contains("gtbank") || lowerBody.contains("gtbank") -> "GTBank"
                lowerSender.contains("zenith") || lowerBody.contains("zenith") -> "Zenith Bank"
                lowerSender.contains("absa") || lowerBody.contains("absa") -> "Absa Bank"
                lowerSender.contains("fidelity") || lowerBody.contains("fidelity") -> "Fidelity Bank"
                lowerSender.contains("gcb") || lowerBody.contains("gcb") -> "GCB Bank"
                lowerSender.contains("calbank") || lowerBody.contains("calbank") -> "CalBank"
                lowerSender.contains("access") || lowerBody.contains("access") -> "Access Bank"
                else -> "Bank Alert"
            }
            isUtility -> "Utility Service"
            else -> "SMS Ingestion"
        }

        // -------------------------------------------------------------
        // 2. Identify Currency
        // -------------------------------------------------------------
        var currency = "GHS"
        when {
            cleanBody.contains("USD") || cleanBody.contains("$") -> currency = "USD"
            cleanBody.contains("EUR") || cleanBody.contains("€") -> currency = "EUR"
            cleanBody.contains("GBP") || cleanBody.contains("£") -> currency = "GBP"
            cleanBody.contains("NGN") || cleanBody.contains("₦") -> currency = "NGN"
            cleanBody.contains("KES") || cleanBody.contains("KSH") -> currency = "KES"
            cleanBody.contains("ZAR") -> currency = "ZAR"
            else -> currency = "GHS"
        }

        // -------------------------------------------------------------
        // 3. Extract External Reference ID (Financial Transaction ID / Txn ID)
        // -------------------------------------------------------------
        var externalRef: String? = null
        val financialTxnPattern = Pattern.compile(
            """(?:Financial\s*Transaction\s*Id|Txn\s*ID|TxnId|Transaction\s*ID|Trans\s*ID|TransId)\s*[:.]?\s*([A-Za-z0-9\-_]{5,32})""",
            Pattern.CASE_INSENSITIVE
        )
        val financialTxnMatcher = financialTxnPattern.matcher(cleanBody)
        if (financialTxnMatcher.find()) {
            externalRef = financialTxnMatcher.group(1)?.trim()
        }

        if (externalRef == null) {
            val genericRefPattern = Pattern.compile(
                """(?:Ref\s*ID|Reference\s*ID|Ref\s*No|Reference\s*No|Reference|Ref\.?|Approval\s*Code|ID[:.]?)\s*[:.]?\s*([A-Za-z0-9\-_]{6,32})""",
                Pattern.CASE_INSENSITIVE
            )
            val genericRefMatcher = genericRefPattern.matcher(cleanBody)
            if (genericRefMatcher.find()) {
                externalRef = genericRefMatcher.group(1)?.trim()
            }
        }

        // -------------------------------------------------------------
        // 4. Extract Amount
        // -------------------------------------------------------------
        var amount: Double? = null
        val explicitAmountPattern = Pattern.compile(
            """(?:GHS|GH[Cc¢₵\u20B5\u01B5]|USD|\$|EUR|€|GBP|£|NGN|KES|ZAR|amount\s*of|paid|received|sent|debited|credited|for)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{2})|[0-9]+(?:\.[0-9]{2})?)""",
            Pattern.CASE_INSENSITIVE
        )
        val explicitAmountMatcher = explicitAmountPattern.matcher(cleanBody)
        if (explicitAmountMatcher.find()) {
            val amtStr = explicitAmountMatcher.group(1)?.replace(",", "")
            amount = amtStr?.toDoubleOrNull()
        }

        if (amount == null) {
            val fallbackPattern = Pattern.compile("""\b([0-9]{1,7}\.[0-9]{2})\b""")
            val fallbackMatcher = fallbackPattern.matcher(cleanBody)
            if (fallbackMatcher.find()) {
                amount = fallbackMatcher.group(1)?.toDoubleOrNull()
            }
        }

        // -------------------------------------------------------------
        // 5. Extract Fee (e-levy / service fee)
        // -------------------------------------------------------------
        var fee: Double? = null
        val feePattern = Pattern.compile(
            """(?:Fee\s*(?:charged|was|is)?|Service\s*Charge|Fee|Service\s*Fee|Tax|Levy)[:\s]*(?:GHS|GH[Cc¢₵\u20B5\u01B5]|USD|\$)?\s*([0-9]+(?:\.[0-9]{2})?)""",
            Pattern.CASE_INSENSITIVE
        )
        val feeMatcher = feePattern.matcher(cleanBody)
        if (feeMatcher.find()) {
            fee = feeMatcher.group(1)?.toDoubleOrNull()
        }

        // -------------------------------------------------------------
        // 6. Extract Meter Number & Recharge Token
        // -------------------------------------------------------------
        var meterNumber: String? = null
        var rechargeToken: String? = null

        val tokenPattern = Pattern.compile("""(?:token|recharge\s*token)[:\s(]*([0-9\- ]{16,24})""", Pattern.CASE_INSENSITIVE)
        val tokenMatcher = tokenPattern.matcher(cleanBody)
        if (tokenMatcher.find()) {
            rechargeToken = tokenMatcher.group(1)?.trim()
        }

        val meterPattern = Pattern.compile("""(?:to\s+meter|meter)\s+([0-9A-Za-z\-]{6,16})""", Pattern.CASE_INSENSITIVE)
        val meterMatcher = meterPattern.matcher(cleanBody)
        if (meterMatcher.find()) {
            meterNumber = meterMatcher.group(1)?.trim()
        }

        // -------------------------------------------------------------
        // 7. Extract Transaction Subtype & Direction
        // -------------------------------------------------------------
        val isCredit = lowerBody.contains("you have received") ||
                lowerBody.contains("received payment") ||
                lowerBody.contains("payment received") ||
                lowerBody.contains("credited with") ||
                lowerBody.contains("credit: acct") ||
                lowerBody.contains("acct credited") ||
                lowerBody.contains("was credited with") ||
                lowerBody.contains("deposit of") ||
                lowerBody.contains("cash in of") ||
                (lowerBody.contains("received") && !lowerBody.contains("received from agent"))

        val direction = if (isCredit) TransactionDirection.CREDIT else TransactionDirection.DEBIT

        val subType: SmsTransactionSubtype = when {
            rechargeToken != null || meterNumber != null || lowerBody.contains("recharge request") || lowerBody.contains("recharge your meter") || lowerBody.contains("ecg") ->
                SmsTransactionSubtype.BILL_RECHARGE

            lowerBody.contains("cash out of") || lowerBody.contains("withdrawn at") || lowerBody.contains("atm wdl") || lowerBody.contains("cash withdrawal") ->
                if (lowerBody.contains("atm")) SmsTransactionSubtype.ATM_WITHDRAWAL else SmsTransactionSubtype.CASH_OUT

            lowerBody.contains("cash in of") || lowerBody.contains("deposit received") ->
                SmsTransactionSubtype.CASH_IN

            lowerBody.contains("airtime") || lowerBody.contains("bundle") || lowerBody.contains("data bundle") ->
                SmsTransactionSubtype.AIRTIME_BUNDLE

            lowerBody.contains("credit: acct") || lowerBody.contains("acct credited") || lowerBody.contains("was credited with") ->
                SmsTransactionSubtype.BANK_CREDIT

            lowerBody.contains("debit: acct") || lowerBody.contains("acct debited") || lowerBody.contains("pos purchase") || lowerBody.contains("pos txn") || lowerBody.contains("debited with") ->
                SmsTransactionSubtype.BANK_DEBIT

            lowerBody.contains("transferred to") || lowerBody.contains("transfer of") || lowerBody.contains("sent to") ->
                SmsTransactionSubtype.P2P_SENT

            isCredit ->
                SmsTransactionSubtype.P2P_RECEIVED

            lowerBody.contains("payment made for") || lowerBody.contains("payment to") || lowerBody.contains("paid to") || lowerBody.contains("paid ghs") ->
                SmsTransactionSubtype.MERCHANT_PAYMENT

            else ->
                if (direction == TransactionDirection.CREDIT) SmsTransactionSubtype.GENERAL_INCOME else SmsTransactionSubtype.GENERAL_EXPENSE
        }

        // -------------------------------------------------------------
        // 8. Extract Counterparty / Payee / Merchant
        // -------------------------------------------------------------
        var counterparty: String? = null
        var counterpartyPhone: String? = null

        // 8a. Meter Recharge Pattern: e.g. "to meter 54310900789 (STEPHEN ETSE)"
        val meterNamePattern = Pattern.compile(
            """(?:to\s+meter|meter)\s+([A-Za-z0-9\-]+)\s*\(([^)]+)\)""",
            Pattern.CASE_INSENSITIVE
        )
        val meterNameMatcher = meterNamePattern.matcher(cleanBody)
        if (meterNameMatcher.find()) {
            val mNum = meterNameMatcher.group(1)?.trim()
            val mName = meterNameMatcher.group(2)?.trim()
            if (!mName.isNullOrBlank()) {
                counterparty = "$mName (Meter $mNum)"
            }
        }

        // 8b. Cash Out / In Agent pattern: e.g. "from Agent 0244111222 - JOE VENTURES was successful"
        if (counterparty == null && (subType == SmsTransactionSubtype.CASH_OUT || subType == SmsTransactionSubtype.CASH_IN)) {
            val agentPattern = Pattern.compile(
                """(?:from\s+Agent|received\s+from\s+Agent)\s+(?:(0\d{9}|\+?233\d{9})\s*[-–—:]*\s*)?([A-Za-z0-9\s&'.-]+?)(?:\s+(?:was\s+successful|has\s+been|\.|\n|$))""",
                Pattern.CASE_INSENSITIVE
            )
            val agentMatcher = agentPattern.matcher(cleanBody)
            if (agentMatcher.find()) {
                counterpartyPhone = agentMatcher.group(1)?.trim()
                val aName = agentMatcher.group(2)?.trim()
                if (!aName.isNullOrBlank()) {
                    counterparty = "$aName (MoMo Agent)"
                }
            }
        }

        // 8c. Bank Alert Desc / Narration / Merchant at: e.g. "Desc: POS PURCHASE - SHOPRITE ACCRA" or "at TOTAL SERVICE STATION"
        if (counterparty == null && isBank) {
            val bankDescPattern = Pattern.compile(
                """(?:Desc|Description|Details|Narration)[:\s]+([^.\n]+?)(?:\s+(?:Date|Bal|Ref|Avail|on\s+\d)|$)""",
                Pattern.CASE_INSENSITIVE
            )
            val bankDescMatcher = bankDescPattern.matcher(cleanBody)
            if (bankDescMatcher.find()) {
                var desc = bankDescMatcher.group(1)?.trim() ?: ""
                desc = desc.replace(Regex("(?i)^(?:pos purchase|pos txn|web purchase|purchase|atm wdl|w/d|transfer to|bill payment)"), "").trim()
                desc = desc.replace(Regex("^[-–—\\s:]+"), "").trim()
                if (desc.isNotBlank()) {
                    counterparty = desc
                }
            }

            if (counterparty == null) {
                val bankAtPattern = Pattern.compile(
                    """(?:at|for\s+pos\s+transaction\s+at)\s+([A-Za-z0-9\s&'.-]+?)(?:\s+(?:Avail|Bal|Ref|Date|on\s+\d|\.|\n|$))""",
                    Pattern.CASE_INSENSITIVE
                )
                val bankAtMatcher = bankAtPattern.matcher(cleanBody)
                if (bankAtMatcher.find()) {
                    val atName = bankAtMatcher.group(1)?.trim()
                    if (!atName.isNullOrBlank() && atName.length in 2..50) {
                        counterparty = atName
                    }
                }
            }
        }

        // 8d. Standard MoMo / P2P Pattern: e.g. "Payment made for GHS 120.00 to KFC ACCRA AIRPORT." or "to 0244123456 - KOFI MENSAH"
        if (counterparty == null) {
            val targetRegex = when (direction) {
                TransactionDirection.CREDIT ->
                    Pattern.compile(
                        """(?:received\s+from|from|received\s+via)\s+([A-Za-z0-9\s\-()&'.]+?)(?:\.\s*(?:Current|Bal|Ref|Txn|Fee|is)|\s+has\s+been|\s+on\s+\d|\s*$|\.)""",
                        Pattern.CASE_INSENSITIVE
                    )
                else ->
                    Pattern.compile(
                        """(?:payment\s+made\s+for[^\n]+to|payment\s+of[^\n]+to|payment\s+to|paid\s+to|paid\s+ghs[^\n]+to|sent\s+to|made\s+to|transferred\s+to|transfer\s+of[^\n]+to|transfer\s+to|to)\s+([A-Za-z0-9\s\-()&'.]+?)(?:\.\s*(?:Current|Bal|Ref|Txn|Fee|is)|\s+has\s+been|\s+was\s+successful|\s+on\s+\d|\s*$|\.)""",
                        Pattern.CASE_INSENSITIVE
                    )
            }

            val matcher = targetRegex.matcher(cleanBody)
            while (matcher.find()) {
                var candidate = matcher.group(1)?.trim() ?: ""
                val lowerCandidate = candidate.lowercase(Locale.ROOT)
                val isActionPhrase = lowerCandidate.startsWith("recharge your meter") ||
                        lowerCandidate.startsWith("recharge meter") ||
                        lowerCandidate.startsWith("use this token") ||
                        lowerCandidate.startsWith("buy bundle") ||
                        lowerCandidate.startsWith("pay bill") ||
                        lowerCandidate.startsWith("cash out") ||
                        lowerCandidate.startsWith("cash in")

                if (candidate.isNotBlank() && candidate.length in 2..60 && !isActionPhrase) {
                    // Extract phone number if preceding or trailing: e.g. "0244123456 - KOFI MENSAH" or "KOFI MENSAH (0244123456)"
                    val leadingPhonePattern = Pattern.compile("""^(0\d{9}|\+?233\d{9})\s*[-–—:]*\s*""")
                    val leadingPhoneMatcher = leadingPhonePattern.matcher(candidate)
                    if (leadingPhoneMatcher.find()) {
                        counterpartyPhone = leadingPhoneMatcher.group(1)
                        candidate = candidate.substring(leadingPhoneMatcher.end()).trim()
                    }

                    val trailingParenPhonePattern = Pattern.compile("""\((0\d{9}|\+?233\d{9})\)""")
                    val trailingParenPhoneMatcher = trailingParenPhonePattern.matcher(candidate)
                    if (trailingParenPhoneMatcher.find()) {
                        counterpartyPhone = trailingParenPhoneMatcher.group(1)
                        candidate = candidate.replace(trailingParenPhonePattern.toRegex(), "").trim()
                    }

                    if (candidate.isNotBlank()) {
                        counterparty = candidate
                        break
                    }
                }
            }
        }

        // 8d. Fallback if Cash Out or Airtime
        if (counterparty == null) {
            counterparty = when (subType) {
                SmsTransactionSubtype.CASH_OUT -> "Cash Out (MoMo Agent)"
                SmsTransactionSubtype.CASH_IN -> "Cash In (MoMo Agent)"
                SmsTransactionSubtype.AIRTIME_BUNDLE -> "Airtime & Data Recharge"
                SmsTransactionSubtype.ATM_WITHDRAWAL -> "ATM Cash Withdrawal"
                SmsTransactionSubtype.BILL_RECHARGE -> "ECG Meter Recharge"
                else -> if (direction == TransactionDirection.CREDIT) "Payment Received" else "Payment Disbursed"
            }
        }

        // -------------------------------------------------------------
        // 9. Extract Ending Balance
        // -------------------------------------------------------------
        var endingBalance: Double? = null
        val balPattern = Pattern.compile(
            """(?:Current\s*Balance|Bal[:.]?|Balance[:.]?|Available\s*Balance|Avail\s*Bal|New\s*balance\s*is)\s*(?:is\s*)?(?:GHS|GH[Cc¢₵\u20B5\u01B5]|USD|\$|NGN|KES|ZAR)?\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{2})|[0-9]+(?:\.[0-9]{2})?)""",
            Pattern.CASE_INSENSITIVE
        )
        val balMatcher = balPattern.matcher(cleanBody)
        if (balMatcher.find()) {
            val balStr = balMatcher.group(1)?.replace(",", "")
            endingBalance = balStr?.toDoubleOrNull()
        }

        // -------------------------------------------------------------
        // 10. Extract Date & Time
        // -------------------------------------------------------------
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

        // -------------------------------------------------------------
        // 11. Category Suggestion
        // -------------------------------------------------------------
        val cpLower = (counterparty ?: "").lowercase(Locale.ROOT)
        val suggestedCategory = when (subType) {
            SmsTransactionSubtype.BILL_RECHARGE -> "Bills & Utilities"
            SmsTransactionSubtype.AIRTIME_BUNDLE -> "Bills & Utilities"
            SmsTransactionSubtype.BANK_CREDIT, SmsTransactionSubtype.P2P_RECEIVED -> "Income"
            else -> when {
                cpLower.contains("kfc") || cpLower.contains("restaurant") || cpLower.contains("chop") ||
                        cpLower.contains("pizza") || cpLower.contains("inn") || cpLower.contains("food") -> "Food & Dining"
                cpLower.contains("bolt") || cpLower.contains("uber") || cpLower.contains("fuel") ||
                        cpLower.contains("shell") || cpLower.contains("total") || cpLower.contains("goil") -> "Transportation"
                cpLower.contains("shoprite") || cpLower.contains("melcom") || cpLower.contains("game") ||
                        cpLower.contains("market") || cpLower.contains("groceries") -> "Shopping & Groceries"
                else -> "General"
            }
        }

        // -------------------------------------------------------------
        // 12. Robust Financial Verification Gate
        // -------------------------------------------------------------
        val isAuthorized = isAuthorizedFinancialSender(sender, cleanBody)
        val isPromo = isPromotionalOrManagementMessage(cleanBody, sender)
        val hasProof = hasVerifiableTransactionProof(
            cleanBody = cleanBody,
            lowerBody = lowerBody,
            externalRef = externalRef,
            endingBalance = endingBalance,
            subType = subType
        )

        // Strict Financial Transaction Ingestion Rules:
        // 1. Must contain valid monetary amount > 0
        // 2. Sender must be an authorized financial provider or utility rail
        // 3. Must not be promotional or management notices (unless carrying both an authoritative ref AND ending balance)
        // 4. Must satisfy verifiable financial proof (Transaction ID, Balance statement, or definite execution verb)
        val isFinancial = when {
            amount == null || amount <= 0.0 -> false
            !isAuthorized -> false
            isPromo && (externalRef == null || endingBalance == null) -> false
            !hasProof -> false
            else -> true
        }

        val rejectionReason = when {
            amount == null || amount <= 0.0 -> "No monetary amount detected in message"
            !isAuthorized -> "Sender '$sender' is not a recognized financial institution or payment rail"
            isPromo && (externalRef == null || endingBalance == null) -> "Message identified as promotional advertisement, bundle offer, or service notice"
            !hasProof -> "Message lacks mandatory transaction verification proof (no transaction ID, balance, or execution confirmation)"
            else -> null
        }

        return ParsedSmsResult(
            isFinancial = isFinancial,
            isPromotional = isPromo,
            rejectionReason = rejectionReason,
            externalRef = externalRef,
            amount = amount,
            fee = fee,
            currency = currency,
            direction = direction,
            subType = subType,
            counterparty = counterparty ?: if (direction == TransactionDirection.CREDIT) "Payment Received" else "Payment Disbursed",
            counterpartyPhone = counterpartyPhone,
            meterNumber = meterNumber,
            rechargeToken = rechargeToken,
            endingBalance = endingBalance,
            timestamp = timestamp,
            rawBody = cleanBody,
            provider = provider,
            suggestedCategory = suggestedCategory,
            confidenceScore = if (externalRef != null && amount != null) 0.98 else 0.85,
            isAiParsed = false
        )
    }
}
