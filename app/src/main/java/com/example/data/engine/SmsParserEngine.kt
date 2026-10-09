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
    val referenceMemo: String? = null,
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
        if (direction == TransactionDirection.CREDIT) {
            noteParts.add("Received from $counterparty via $provider")
        } else {
            when (subType) {
                SmsTransactionSubtype.CASH_OUT -> noteParts.add("Cash out made to $counterparty via $provider")
                SmsTransactionSubtype.AIRTIME_BUNDLE -> noteParts.add("Airtime payment to $counterparty via $provider")
                else -> noteParts.add("Payment made to $counterparty via $provider")
            }
        }
        if (!referenceMemo.isNullOrBlank()) {
            noteParts.add("Ref: $referenceMemo")
        }
        if (fee != null && fee > 0) {
            noteParts.add("Fee: $currency ${String.format(Locale.US, "%.2f", fee)}")
        }
        if (!externalRef.isNullOrBlank()) {
            noteParts.add("Txn ID: $externalRef")
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
            isVerified = false,
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
            "airteltigo", "at money", "atmoney", "airteltigomoney", "airtel cash", "airtel"
        )
        val AUTHORIZED_BANK_SENDERS = listOf(
            "ecobank", "stanbic", "gcb", "gtbank", "zenith", "absa", "fidelity",
            "calbank", "access", "standard chartered", "stanchart", "fnb", "uba",
            "republic", "cbg", "omnibsic", "prudential", "first atlantic", "boa", "bank"
        )
        val AUTHORIZED_UTILITY_SENDERS = listOf(
            "zeepay", "g-money", "gmoney"
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

    fun isAuthorizedFinancialSender(sender: String, body: String, allowBodyInference: Boolean = true): Boolean {
        val s = sender.lowercase(Locale.ROOT).trim()
        val b = body.lowercase(Locale.ROOT)

        // Exclude ECG and utility tokens strictly per tracking rules (Mobile Money alert is tracked instead):
        if (s.contains("ecg") || b.contains("ecg") ||
            s.contains("powerapp") || b.contains("powerapp") ||
            s.contains("gwcl") || b.contains("gwcl") ||
            b.contains("meter token") || b.contains("recharge your meter") ||
            b.contains("to meter") || b.contains("meter number") || b.contains("meter no") ||
            b.contains("recharge token") || (b.contains("token") && b.contains("meter"))
        ) {
            return false
        }

        if (AUTHORIZED_MOMO_SENDERS.any { s.contains(it) }) return true
        if (AUTHORIZED_TELECEL_SENDERS.any { s.contains(it) }) return true
        if (AUTHORIZED_AT_SENDERS.any { s.contains(it) }) return true
        if (AUTHORIZED_BANK_SENDERS.any { s.contains(it) }) return true
        if (AUTHORIZED_UTILITY_SENDERS.any { s.contains(it) }) return true

        // If sender is unknown/empty/generic, check for unambiguous cryptographic/financial header in body:
        val hasExplicitMoMoHeader = b.contains("financial transaction id") ||
                b.contains("mobilemoney") ||
                b.contains("telecel cash") ||
                b.contains("t-cash") ||
                b.contains("at money") ||
                b.contains("airtel cash") ||
                b.contains("payment made for") ||
                b.contains("payment for") ||
                b.contains("payment received for") ||
                b.contains("cash out made for") ||
                b.contains("cash out of") ||
                b.contains("cash in received for") ||
                b.contains("cash in of") ||
                b.contains("you have transferred") ||
                b.contains("transferred ghs") ||
                b.contains("momo wallet") ||
                b.contains("mtn momo") ||
                b.contains("download the momo app")
        val hasExplicitBankHeader = (b.contains("acct:") || b.contains("acct **") || b.contains("acct no") || b.contains("debit alert") || b.contains("credit alert") || b.contains("amount debited") || b.contains("amount credited")) &&
                (b.contains("avail bal") || b.contains("bal:") || b.contains("balance:") || b.contains("pos purchase") || b.contains("atm wdl") || b.contains("ref:"))

        return allowBodyInference && (hasExplicitMoMoHeader || hasExplicitBankHeader)
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
                lowerBody.contains("payment made") ||
                lowerBody.contains("payment for") ||
                lowerBody.contains("payment of") ||
                lowerBody.contains("payment received for") ||
                lowerBody.contains("payment received") ||
                lowerBody.contains("you have received") ||
                lowerBody.contains("cash out made for") ||
                lowerBody.contains("cash out of") ||
                lowerBody.contains("cash in received for") ||
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
                lowerBody.contains("amount debited") ||
                lowerBody.contains("amount credited")

        return hasDefiniteExecutionVerb
    }

    fun parse(
        sender: String,
        body: String,
        sourceTimestamp: Long? = null,
        allowBodySenderInference: Boolean = true
    ): ParsedSmsResult {
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
                lowerSender.contains("airtel") ||
                lowerBody.contains("at money") ||
                lowerBody.contains("atmoney") ||
                lowerBody.contains("airtel cash")

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
                lowerBody.contains("atm wdl") ||
                lowerBody.contains("amount debited") ||
                lowerBody.contains("amount credited")

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
            else -> "Mobile Money"
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
        // 3. Extract External Reference ID (Financial Transaction ID / TNX ID / Txn ID / Ref ID)
        // -------------------------------------------------------------
        var externalRef: String? = null
        val financialTxnPattern = Pattern.compile(
            """(?:Financial\s*Transaction\s*Id|Transaction\s*ID|Transaction\s*Id|TNX\s*ID|TNXId|TNX|Txn\s*ID|TxnId|TXN|Trans\s*ID|TransId|TRX\s*ID|TRX|TrxId|Approval\s*Code)\s*[:.\s-]*\s*([A-Za-z0-9\-_/.]{1,36})""",
            Pattern.CASE_INSENSITIVE
        )
        val financialTxnMatcher = financialTxnPattern.matcher(cleanBody)
        if (financialTxnMatcher.find()) {
            externalRef = financialTxnMatcher.group(1)?.trim()?.trimEnd('.', ',', ';', ':', '-')
        }

        if (externalRef == null) {
            val genericRefPattern = Pattern.compile(
                """(?:Ref\s*ID|Reference\s*ID|Ref\s*No|Reference\s*No|Ref\.?|Ext\s*Ref|External\s*Ref|ID)\s*[:.\s-]*\s*([A-Za-z0-9\-_/.]{1,36})""",
                Pattern.CASE_INSENSITIVE
            )
            val genericRefMatcher = genericRefPattern.matcher(cleanBody)
            if (genericRefMatcher.find()) {
                val cand = genericRefMatcher.group(1)?.trim()?.trimEnd('.', ',', ';', ':', '-')
                if (!cand.isNullOrBlank()) {
                    externalRef = cand
                }
            }
        }

        // Reference / Memo text extraction (e.g. "Reference: Airtime.", "Reference: Food")
        var referenceMemo: String? = null
        val refPattern = Pattern.compile(
            """(?:Reference|Ref)[:\s]+([^.\n\r]+?)(?=[.,\n\r]|\s+(?:Transaction\s*ID|Transaction\s*Id|Financial|Fee|Current|Bal|Available)|$)""",
            Pattern.CASE_INSENSITIVE
        )
        val refMatcher = refPattern.matcher(cleanBody)
        if (refMatcher.find()) {
            val cand = refMatcher.group(1)?.trim()?.trimEnd('.', ',', ';', ':', '-')
            if (!cand.isNullOrBlank() && cand != "." && cand.length in 2..40 && !cand.equals("none", ignoreCase = true)) {
                referenceMemo = cand
            }
        }

        // -------------------------------------------------------------
        // 4. Extract Amount
        // -------------------------------------------------------------
        var amount: Double? = null

        // 4a. Currency symbol/code preceding amount: e.g. "GHS 20.00", "GHS35.00", "GHS5.00", "GHS 30"
        val explicitAmountPattern = Pattern.compile(
            """(?:GHS|GH[Cc¢₵\u20B5\u01B5]|USD|\$|EUR|€|GBP|£|NGN|KES|ZAR)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
            Pattern.CASE_INSENSITIVE
        )
        val explicitAmountMatcher = explicitAmountPattern.matcher(cleanBody)
        if (explicitAmountMatcher.find()) {
            val amtStr = explicitAmountMatcher.group(1)?.replace(",", "")
            amount = amtStr?.toDoubleOrNull()
        }

        // 4b. Amount followed by currency unit: e.g. "50 Ghana cedis", "50 cedis", "35 ghana cedis"
        if (amount == null) {
            val suffixAmountPattern = Pattern.compile(
                """([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:ghana\s*cedis|cedis|ghs|ghc)\b""",
                Pattern.CASE_INSENSITIVE
            )
            val suffixMatcher = suffixAmountPattern.matcher(cleanBody)
            if (suffixMatcher.find()) {
                val amtStr = suffixMatcher.group(1)?.replace(",", "")
                amount = amtStr?.toDoubleOrNull()
            }
        }

        // 4c. Preposition amount: e.g. "for 20.00", "for 50", "of 35.00"
        if (amount == null) {
            val prepAmountPattern = Pattern.compile(
                """(?:for|of|amount\s*of|amt:?)\s*(?:GHS|GH[Cc¢₵\u20B5\u01B5]|USD|\$)?\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
                Pattern.CASE_INSENSITIVE
            )
            val prepMatcher = prepAmountPattern.matcher(cleanBody)
            if (prepMatcher.find()) {
                val amtStr = prepMatcher.group(1)?.replace(",", "")
                amount = amtStr?.toDoubleOrNull()
            }
        }

        // 4d. Fallback decimal pattern e.g. "20.00"
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
            """(?:Fee\s*(?:charged|was|is)?|TRANSACTION\s*FEE|Service\s*Charge|Fee|Service\s*Fee|Tax|Levy)[:\s]*(?:GHS|GH[Cc¢₵\u20B5\u01B5]|USD|\$)?\s*([0-9]+(?:\.[0-9]{1,2})?)""",
            Pattern.CASE_INSENSITIVE
        )
        val feeMatcher = feePattern.matcher(cleanBody)
        if (feeMatcher.find()) {
            fee = feeMatcher.group(1)?.toDoubleOrNull()
        }

        // -------------------------------------------------------------
        // 6. Extract Transaction Subtype & Direction
        // -------------------------------------------------------------
        val isCredit = lowerBody.contains("you have received") ||
                lowerBody.contains("received payment") ||
                lowerBody.contains("payment received") ||
                lowerBody.contains("cash in") ||
                lowerBody.contains("credited with") ||
                lowerBody.contains("credit: acct") ||
                lowerBody.contains("acct credited") ||
                lowerBody.contains("was credited with") ||
                lowerBody.contains("credit alert") ||
                lowerBody.contains("amount credited") ||
                lowerBody.contains("deposit of") ||
                lowerBody.contains("deposit received") ||
                (lowerBody.contains("received") && !lowerBody.contains("received from agent") && !lowerBody.contains("payment made"))

        val direction = if (isCredit) TransactionDirection.CREDIT else TransactionDirection.DEBIT

        val subType: SmsTransactionSubtype = when {
            lowerBody.contains("cash out") || lowerBody.contains("cashout") || lowerBody.contains("withdrawn at") || lowerBody.contains("atm wdl") || lowerBody.contains("cash withdrawal") ->
                if (lowerBody.contains("atm")) SmsTransactionSubtype.ATM_WITHDRAWAL else SmsTransactionSubtype.CASH_OUT

            lowerBody.contains("cash in") || lowerBody.contains("deposit received") || lowerBody.contains("deposit of") ->
                SmsTransactionSubtype.CASH_IN

            lowerBody.contains("airtime") || lowerBody.contains("data bundle") ->
                SmsTransactionSubtype.AIRTIME_BUNDLE

            lowerBody.contains("credit alert") || lowerBody.contains("acct credited") || lowerBody.contains("was credited with") || lowerBody.contains("credit: acct") || lowerBody.contains("amount credited") ->
                SmsTransactionSubtype.BANK_CREDIT

            lowerBody.contains("debit alert") || lowerBody.contains("acct debited") || lowerBody.contains("pos purchase") || lowerBody.contains("pos txn") || lowerBody.contains("debited with") || lowerBody.contains("debit: acct") || lowerBody.contains("amount debited") ->
                SmsTransactionSubtype.BANK_DEBIT

            lowerBody.contains("transferred to") || lowerBody.contains("transfer of") || lowerBody.contains("sent to") || lowerBody.contains("you have sent") ->
                SmsTransactionSubtype.P2P_SENT

            isCredit ->
                SmsTransactionSubtype.P2P_RECEIVED

            lowerBody.contains("payment made for") || lowerBody.contains("payment for") || lowerBody.contains("payment to") || lowerBody.contains("paid to") || lowerBody.contains("paid ghs") ->
                SmsTransactionSubtype.MERCHANT_PAYMENT

            else ->
                if (direction == TransactionDirection.CREDIT) SmsTransactionSubtype.GENERAL_INCOME else SmsTransactionSubtype.GENERAL_EXPENSE
        }

        // -------------------------------------------------------------
        // 7. Extract Counterparty / Payee / Merchant
        // -------------------------------------------------------------
        var counterparty: String? = null
        var counterpartyPhone: String? = null

        val stopLookahead = """(?=[.,;\n\r]|\s+(?:Current\s*Balance|Available\s*Balance|Avail\s*Bal|New\s*balance|Bal[:.]?|Balance[:.]?|Reference|Ref[:.]?|Transaction\s*ID|Transaction\s*Id|Financial\s*Transaction\s*Id|Trans\s*ID|Txn\s*ID|Fee\s*charged|TRANSACTION\s*FEE|Fee|Tax\s*charged|Download\s*the\s*MoMo|Cash-out\s*fee|Cash\s*in\s*\(Deposit\)|Please\s*do\s*not|Thank\s*you|Date[:.]?|Time[:.]?|on\s+\d{1,2}[-/.]\d{1,2}|was\s+successful|has\s+been|Click\s*here)|$)"""

        // 7a. Cash Out / In Agent pattern: e.g. "from Agent 0244111222 - JOE VENTURES was successful"
        if (subType == SmsTransactionSubtype.CASH_OUT || subType == SmsTransactionSubtype.CASH_IN) {
            val agentPattern = Pattern.compile(
                """(?:from\s+Agent|received\s+from\s+Agent|at\s+Agent)\s+(?:(0\d{9}|\+?233\d{9})\s*[-–—:]*\s*)?([A-Za-z0-9\s&'.-]+?)$stopLookahead""",
                Pattern.CASE_INSENSITIVE
            )
            val agentMatcher = agentPattern.matcher(cleanBody)
            if (agentMatcher.find()) {
                counterpartyPhone = agentMatcher.group(1)?.trim()
                val aName = agentMatcher.group(2)?.trim()
                if (!aName.isNullOrBlank() && aName.length in 2..50) {
                    counterparty = "$aName (MoMo Agent)"
                }
            }
        }

        // 7b. Bank Alert Desc / Narration / Merchant: e.g. "Desc: POS PURCHASE - SHOPRITE ACCRA" or "at TOTAL SERVICE STATION"
        if (counterparty == null && isBank) {
            val bankDescPattern = Pattern.compile(
                """(?:Desc|Description|Details|Narration|Merchant)[:\s]+([^.\n]+?)$stopLookahead""",
                Pattern.CASE_INSENSITIVE
            )
            val bankDescMatcher = bankDescPattern.matcher(cleanBody)
            if (bankDescMatcher.find()) {
                var desc = bankDescMatcher.group(1)?.trim() ?: ""
                desc = desc.replace(Regex("(?i)^(?:pos purchase|pos txn|web purchase|purchase|atm wdl|w/d|transfer to|bill payment|salary credit)"), "").trim()
                desc = desc.replace(Regex("^[-–—\\s:]+"), "").trim()
                if (desc.isNotBlank()) {
                    counterparty = desc
                }
            }

            if (counterparty == null) {
                val bankAtPattern = Pattern.compile(
                    """(?:at|for\s+pos\s+transaction\s+at)\s+([A-Za-z0-9\s&'.-]+?)$stopLookahead""",
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

        // 7c. Credit / Income: "from <Payer/Sender/Merchant>"
        // Handles:
        // "Payment received for 50 Ghana cedis from Andrews Yamoah"
        // "Cash In received for GHS 20.00 from DUPEZ BUSINESS CENTRE"
        // "Payment received for GHS 30.00 from ANDREWS NYAME"
        // "Cash In received for GHS 20.00 from OCEAN SKY INTERNATIONAL COMPANY LIMITED"
        if (counterparty == null && direction == TransactionDirection.CREDIT) {
            val fromPattern = Pattern.compile(
                """(?:cash\s+in\s+(?:received\s+)?(?:for|of)?\s*[^.\n]*?\s*from|payment\s+received\s+(?:for|of)?\s*[^.\n]*?\s*from|received\s+(?:payment\s+)?(?:for|of)?\s*[^.\n]*?\s*from|you\s+have\s+received\s+[^.\n]*?\s*from|credited\s+(?:with\s+)?[^.\n]*?\s*from|deposit\s+received\s+from|deposit\s+from|from)\s+([A-Za-z0-9\s&'./-]+?)$stopLookahead""",
                Pattern.CASE_INSENSITIVE
            )
            val fromMatcher = fromPattern.matcher(cleanBody)
            while (fromMatcher.find()) {
                val candidate = fromMatcher.group(1)?.trim()?.trimEnd('.', ',', ';', ':', '-', '–', '—')
                if (!candidate.isNullOrBlank() && candidate.length in 2..60 && !candidate.equals("payment received", ignoreCase = true) && !candidate.equals("cash in", ignoreCase = true)) {
                    counterparty = candidate
                    break
                }
            }
        }

        // 7d. Debit / Expense: "to <Payee/Recipient/Merchant>"
        // Handles:
        // "Payment made for GHS 20.00 to PHILLIPA BUABENG"
        // "Cash Out made for GHS35.00 to EMELIA ARTHUR"
        // "Payment for GHS5.00 to KGL ..Current Balance: GHS 5.90"
        // "Cashout made for 35 Ghana cedis to Emilia Atta"
        // "Payment made for payment made for 200 Ghana cedis to Philip Ababio"
        // "You have transferred GHS 85.00 to AMA BOATENG"
        if (counterparty == null && direction == TransactionDirection.DEBIT) {
            val toPattern = Pattern.compile(
                """(?:cash\s+out\s+(?:made\s+)?(?:for|of)?\s*[^.\n]*?\s*to|cashout\s+(?:made\s+)?(?:for|of)?\s*[^.\n]*?\s*to|payment\s+made\s+(?:for|of)?\s*[^.\n]*?\s*to|payment\s+for\s+[^.\n]*?\s*to|paid\s+(?:for\s+)?[^.\n]*?\s*to|transferred\s+[^.\n]*?\s*to|you\s+have\s+transferred\s+[^.\n]*?\s*to|you\s+have\s+sent\s+[^.\n]*?\s*to|sent\s+[^.\n]*?\s*to|debited\s+[^.\n]*?\s*to|made\s+to|to)\s+([A-Za-z0-9\s&'./-]+?)$stopLookahead""",
                Pattern.CASE_INSENSITIVE
            )
            val toMatcher = toPattern.matcher(cleanBody)
            while (toMatcher.find()) {
                val candidate = toMatcher.group(1)?.trim()?.trimEnd('.', ',', ';', ':', '-', '–', '—')
                if (!candidate.isNullOrBlank() && candidate.length in 2..60 && !candidate.equals("payment made", ignoreCase = true) && !candidate.equals("cash out", ignoreCase = true)) {
                    counterparty = candidate
                    break
                }
            }
        }

        // 7e. Explicit Payee / Recipient / Sender label matching
        if (counterparty == null) {
            val labelPattern = Pattern.compile(
                """(?:Paid\s+to|Payee|Recipient|Beneficiary|Merchant|Merchant\s*Name|To\s*Name|Vendor|Sender|Sender\s*Name|From\s*Name|Payer)\s*[:.\s-]+\s*([A-Za-z0-9\s\-()&'.]+?)$stopLookahead""",
                Pattern.CASE_INSENSITIVE
            )
            val labelMatcher = labelPattern.matcher(cleanBody)
            if (labelMatcher.find()) {
                val cand = labelMatcher.group(1)?.trim()?.trimEnd('.', ',', ';', ':', '-')
                if (!cand.isNullOrBlank() && cand.length in 2..60) {
                    counterparty = cand
                }
            }
        }

        // 7f. Secondary fallback: parse clean word sequence after "from" or "to"
        if (counterparty == null) {
            val simpleRegex = if (direction == TransactionDirection.CREDIT) {
                Pattern.compile("""\bfrom\s+([A-Za-z0-9\s&'.-]{3,40})$stopLookahead""", Pattern.CASE_INSENSITIVE)
            } else {
                Pattern.compile("""\b(?:to|paid)\s+([A-Za-z0-9\s&'.-]{3,40})$stopLookahead""", Pattern.CASE_INSENSITIVE)
            }
            val sMatcher = simpleRegex.matcher(cleanBody)
            if (sMatcher.find()) {
                val cand = sMatcher.group(1)?.trim()?.trimEnd('.', ',', ';', ':')
                if (!cand.isNullOrBlank() && cand.length in 2..40 && !cand.lowercase().contains("balance") && !cand.lowercase().contains("acct")) {
                    counterparty = cand
                }
            }
        }

        // 7g. Clean phone number and formatting in counterparty
        if (counterparty != null) {
            var cand = counterparty!!
            val leadingPhonePattern = Pattern.compile("""^(0\d{9}|\+?233\d{9})\s*[-–—:]*\s*""")
            val leadingPhoneMatcher = leadingPhonePattern.matcher(cand)
            if (leadingPhoneMatcher.find()) {
                counterpartyPhone = leadingPhoneMatcher.group(1)
                cand = cand.substring(leadingPhoneMatcher.end()).trim()
            }

            val trailingParenPhonePattern = Pattern.compile("""\((0\d{9}|\+?233\d{9})\)""")
            val trailingParenPhoneMatcher = trailingParenPhonePattern.matcher(cand)
            if (trailingParenPhoneMatcher.find()) {
                counterpartyPhone = trailingParenPhoneMatcher.group(1)
                cand = cand.replace(trailingParenPhonePattern.toRegex(), "").trim()
            }

            val trailingPhonePattern = Pattern.compile("""[-–—:]*\s*(0\d{9}|\+?233\d{9})$""")
            val trailingPhoneMatcher = trailingPhonePattern.matcher(cand)
            if (trailingPhoneMatcher.find()) {
                if (counterpartyPhone == null) counterpartyPhone = trailingPhoneMatcher.group(1)
                cand = cand.substring(0, trailingPhoneMatcher.start()).trim()
            }

            // Remove noise words
            cand = cand.replace(Regex("(?i)\\s+(?:was successful|has been|is successful)$"), "").trim()
            cand = cand.trimEnd('.', ',', ';', ':', '-', '–', '—').trim()

            if (cand.isNotBlank() && cand.length in 2..60 && !cand.equals("payment received", ignoreCase = true) && !cand.equals("payment disbursed", ignoreCase = true)) {
                counterparty = cand
            } else if (counterpartyPhone != null) {
                counterparty = if (direction == TransactionDirection.CREDIT) "MoMo Sender ($counterpartyPhone)" else "MoMo Recipient ($counterpartyPhone)"
            } else {
                counterparty = null
            }
        }

        // 7h. Semantic Fallback (NEVER use "Payment Received" as counterparty name)
        if (counterparty == null) {
            counterparty = when (subType) {
                SmsTransactionSubtype.CASH_OUT -> "MoMo Cash Out"
                SmsTransactionSubtype.CASH_IN -> "MoMo Deposit"
                SmsTransactionSubtype.AIRTIME_BUNDLE -> "Airtime & Bundle"
                SmsTransactionSubtype.ATM_WITHDRAWAL -> "ATM Cash Withdrawal"
                SmsTransactionSubtype.BANK_DEBIT -> "Bank Payment"
                SmsTransactionSubtype.BANK_CREDIT -> "Bank Deposit"
                else -> if (direction == TransactionDirection.CREDIT) "MoMo Deposit" else "MoMo Payment"
            }
        }

        // -------------------------------------------------------------
        // 8. Extract Ending Balance
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
        // 9. Extract Date & Time
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
        // 10. Category Suggestion
        // -------------------------------------------------------------
        val cpLower = counterparty.lowercase(Locale.ROOT)
        val suggestedCategory = when (subType) {
            SmsTransactionSubtype.AIRTIME_BUNDLE -> "Bills & Utilities"
            SmsTransactionSubtype.BANK_CREDIT, SmsTransactionSubtype.P2P_RECEIVED, SmsTransactionSubtype.CASH_IN -> "Income"
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
        // 11. Robust Financial Verification Gate
        // -------------------------------------------------------------
        val isAuthorized = isAuthorizedFinancialSender(sender, cleanBody, allowBodySenderInference)
        val isPromo = isPromotionalOrManagementMessage(cleanBody, sender)
        val hasProof = hasVerifiableTransactionProof(
            cleanBody = cleanBody,
            lowerBody = lowerBody,
            externalRef = externalRef,
            endingBalance = endingBalance,
            subType = subType
        )

        val isFinancial = when {
            amount == null || amount <= 0.0 -> false
            !isAuthorized -> false
            isPromo && (externalRef == null || endingBalance == null) -> false
            !hasProof -> false
            else -> true
        }

        val rejectionReason = when {
            amount == null || amount <= 0.0 -> "No monetary amount detected in message"
            !isAuthorized -> "Sender '$sender' is not a recognized Mobile Money or Bank payment rail, or message is an excluded utility receipt"
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
            counterparty = counterparty,
            counterpartyPhone = counterpartyPhone,
            referenceMemo = referenceMemo,
            meterNumber = null,
            rechargeToken = null,
            endingBalance = endingBalance,
            timestamp = sourceTimestamp ?: timestamp,
            rawBody = cleanBody,
            provider = provider,
            suggestedCategory = suggestedCategory,
            confidenceScore = if (externalRef != null && amount != null) 0.98 else 0.85,
            isAiParsed = false
        )
    }
}
