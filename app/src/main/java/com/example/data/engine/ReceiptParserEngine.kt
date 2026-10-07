package com.example.data.engine

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

data class ReceiptDraft(
    val amount: Double? = null,
    val merchant: String? = null,
    val date: Long = System.currentTimeMillis(),
    val tax: Double? = null,
    val confidenceScore: Double = 0.5,
    val currency: String = "GHS",
    val referenceNumber: String? = null,
    val transactionType: String = "EXPENSE", // "EXPENSE", "INCOME", "TRANSFER", "BILL_PAYMENT"
    val suggestedCategory: String? = null,
    val paymentRail: String? = null,
    val rawText: String? = null,
    val isAiEnhanced: Boolean = false,
    val itemsSummary: String? = null
)

class ReceiptParserEngine {

    fun parse(rawText: String): ReceiptDraft {
        val lines = rawText.split(Regex("\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 1. Detect Currency (Default to GHS)
        var currency = "GHS"
        val upper = rawText.uppercase(Locale.ROOT)
        when {
            upper.contains("USD") || upper.contains("$") -> currency = "USD"
            upper.contains("EUR") || upper.contains("€") -> currency = "EUR"
            upper.contains("GBP") || upper.contains("£") -> currency = "GBP"
            upper.contains("NGN") || upper.contains("₦") -> currency = "NGN"
            upper.contains("KES") || upper.contains("KSH") -> currency = "KES"
            upper.contains("ZAR") || upper.contains(" R ") -> currency = "ZAR"
            else -> currency = "GHS"
        }

        // 2. Extract Grand Total
        var total: Double? = null
        val explicitTotalPattern = Pattern.compile(
            """(?:grand\s+total|total\s+amount|net\s+total|amount\s+paid|total\s+due|total)[^\d]{0,14}(?:GHS|GH[Cc¢₵\u20B5\u01B5]|USD|EUR|GBP|\$|€|£)?\s*(\d{1,7}[.,]\d{2})""",
            Pattern.CASE_INSENSITIVE
        )
        val explicitTotalMatcher = explicitTotalPattern.matcher(rawText)
        if (explicitTotalMatcher.find()) {
            val amtStr = explicitTotalMatcher.group(1)?.replace(",", ".")
            total = amtStr?.toDoubleOrNull()
        }

        if (total == null) {
            val amountPattern = Pattern.compile("""(?:^|\s)(?:GHS|GH[Cc¢₵\u20B5\u01B5]|USD|EUR|GBP|\$|€|£)?\s*(\d{1,7}[.,]\d{2})(?=\s|$)""")
            val matcher = amountPattern.matcher(rawText)
            val foundAmounts = mutableListOf<Double>()
            while (matcher.find()) {
                val s = matcher.group(1)?.replace(",", ".")
                s?.toDoubleOrNull()?.let { foundAmounts.add(it) }
            }
            if (foundAmounts.isNotEmpty()) {
                total = foundAmounts.maxOrNull()
            }
        }

        // 3. Extract Tax (VAT / NHIL / GETFund / Levy)
        var tax: Double? = null
        val taxPattern = Pattern.compile(
            """(?:tax|vat|nhil|getfund|covid|levy)[^\d]{0,12}(?:GHS|GH[Cc¢₵\u20B5\u01B5]|\$|€|£)?\s*(\d{1,5}[.,]\d{2})""",
            Pattern.CASE_INSENSITIVE
        )
        val taxMatcher = taxPattern.matcher(rawText)
        if (taxMatcher.find()) {
            val taxStr = taxMatcher.group(1)?.replace(",", ".")
            tax = taxStr?.toDoubleOrNull()
        }

        // 4. Extract Reference Number / Transaction ID / Receipt # / Token
        var referenceNumber: String? = null

        // MoMo/ECG Token Pattern
        val tokenPattern = Pattern.compile("""(?:token|recharge\s*token)[:\s(]*(\d{16,24})""", Pattern.CASE_INSENSITIVE)
        val tokenMatcher = tokenPattern.matcher(rawText)
        if (tokenMatcher.find()) {
            referenceNumber = "Token: ${tokenMatcher.group(1)}"
        }

        // Standard Reference / Txn ID / Receipt No / Invoice No Pattern
        if (referenceNumber == null) {
            val refPattern = Pattern.compile(
                """(?:txn\s*id|transaction\s*id|trans\s*id|receipt\s*(?:no|number|#)?|invoice\s*(?:no|number|#)?|ref\s*(?:no|number)?|reference\s*(?:no|number)?|approval\s*code|auth\s*(?:code|no)|stan|meter\s*(?:no|number)?)[:\s#]*([A-Z0-9-]{5,32})""",
                Pattern.CASE_INSENSITIVE
            )
            val refMatcher = refPattern.matcher(rawText)
            if (refMatcher.find()) {
                val candidateRef = refMatcher.group(1)?.trim()
                if (!candidateRef.isNullOrBlank() && candidateRef.any { it.isDigit() }) {
                    referenceNumber = candidateRef
                }
            }
        }

        // Check for trailing "Txn ID: ..." or "Ref: ..." in freeform text
        if (referenceNumber == null) {
            val freeRefPattern = Pattern.compile("""\b([A-Z0-9]{8,20})\b""")
            val freeMatcher = freeRefPattern.matcher(rawText)
            while (freeMatcher.find()) {
                val word = freeMatcher.group(1) ?: ""
                val hasDigit = word.any { it.isDigit() }
                val hasLetter = word.any { it.isLetter() }
                if (hasDigit && hasLetter && word.length in 8..18 && !word.startsWith("202")) {
                    referenceNumber = word
                    break
                }
            }
        }

        // 5. Extract Transaction Type
        val lowerText = rawText.lowercase(Locale.ROOT)
        val transactionType = when {
            lowerText.contains("meter") || lowerText.contains("ecg") || lowerText.contains("recharge") ||
                    lowerText.contains("dstv") || lowerText.contains("gotv") || lowerText.contains("electricity") ||
                    lowerText.contains("water bill") || lowerText.contains("utility") -> "BILL_PAYMENT"

            lowerText.contains("received from") || lowerText.contains("payment received") ||
                    lowerText.contains("credited with") || lowerText.contains("refund") ||
                    lowerText.contains("deposit") || lowerText.contains("cash in") ||
                    lowerText.contains("salary") -> "INCOME"

            lowerText.contains("transferred to") || lowerText.contains("transfer to") ||
                    lowerText.contains("bank transfer") || lowerText.contains("wallet to wallet") -> "TRANSFER"

            else -> "EXPENSE"
        }

        // 6. Extract Merchant / Counterparty Name
        var merchant: String? = null

        // Check for structured Mobile Money / Recharge pattern: "to meter 123 (STEPHEN ETSE)"
        val meterNamePattern = Pattern.compile("""to\s+meter\s+\d+\s*\(([^)]+)\)""", Pattern.CASE_INSENSITIVE)
        val meterNameMatcher = meterNamePattern.matcher(rawText)
        if (meterNameMatcher.find()) {
            val name = meterNameMatcher.group(1)?.trim()
            if (!name.isNullOrBlank()) {
                merchant = toTitleCase(name)
            }
        }

        // Check for Mobile Money "Payment of ... to [NAME]" or "transferred to [NAME]"
        if (merchant == null) {
            val momoPayeePattern = Pattern.compile(
                """(?:payment\s+to|paid\s+to|paid\s+at|transferred\s+to|transfer\s+to|sent\s+to)\s+([A-Za-z0-9\s&'.-]{3,35}?)(?:\s+(?:has|successful|on|at|ref|txn|with|\.|\n|$))""",
                Pattern.CASE_INSENSITIVE
            )
            val momoMatcher = momoPayeePattern.matcher(rawText)
            if (momoMatcher.find()) {
                val rawPayee = momoMatcher.group(1)?.trim()
                if (!rawPayee.isNullOrBlank() && !rawPayee.matches(Regex("(?i).*(meter|account|wallet|cash).*"))) {
                    merchant = toTitleCase(rawPayee)
                }
            }
        }

        // Fallback: Scan lines with intelligent noise scoring
        if (merchant == null) {
            val knownBrands = listOf(
                "MAXMART", "KFC", "SHOPRITE", "MELCOM", "GAME", "PALACE", "SHELL", "TOTALENERGIES", "TOTAL",
                "GOIL", "ALLIED", "PUMA", "PAPAYE", "STARBITES", "CHICKEN INN", "PIZZA HUT",
                "PIZZAMAN", "CHICKENMAN", "JUMIA", "BOLT", "UBER", "YANGO", "ECG", "GHANA WATER",
                "COMPUGHANA", "FRANKIES", "BURGER KING", "SIMPLY DELICIOUS", "BUKA", "MARWABA"
            )

            // Check if any known brand appears in lines
            for (line in lines) {
                val upperLine = line.uppercase(Locale.ROOT)
                for (brand in knownBrands) {
                    if (upperLine.contains(brand)) {
                        merchant = toTitleCase(line)
                        break
                    }
                }
                if (merchant != null) break
            }
        }

        // Still null: pick best candidate line from header
        if (merchant == null) {
            val noiseRegex = Regex(
                "(?i)^(?:receipt|invoice|tax\\s*invoice|cash\\s*sale|sales\\s*slip|customer\\s*copy|merchant\\s*copy|duplicate|" +
                        "official\\s*receipt|simply\\s*the\\s*best|thank\\s*you.*|thanks\\s*for.*|visit\\s*again.*|welcome.*|tel:.*|phone:.*|website.*|" +
                        "www\\..*|email:.*|terminal.*|merchant\\s*id.*|mid:.*|tid:.*|batch.*|stan.*|trace.*|date:.*|time:.*|cashier.*|subtotal.*|total.*|" +
                        "change.*|cash.*|card.*|p\\.?o\\.?\\s*box.*|vat\\s*no.*|tin:.*|accra\\s*,?\\s*ghana|kumasi\\s*,?\\s*ghana)$"
            )

            for (line in lines.take(8)) {
                val hasLetters = line.any { it.isLetter() }
                val isShort = line.length in 3..40
                val isNoise = line.matches(noiseRegex)
                val digitCount = line.count { it.isDigit() }
                if (hasLetters && isShort && !isNoise && digitCount <= line.length / 3) {
                    merchant = toTitleCase(line)
                    break
                }
            }
        }

        // 7. Extract Date
        var timestamp = System.currentTimeMillis()
        val isoDatePattern = Pattern.compile("""\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b""")
        val localDatePattern = Pattern.compile("""\b(\d{1,2})[-/.](\d{1,2})[-/.](20\d{2})\b""")

        val isoMatcher = isoDatePattern.matcher(rawText)
        val localMatcher = localDatePattern.matcher(rawText)

        if (isoMatcher.find()) {
            try {
                val y = isoMatcher.group(1)!!.toInt()
                val m = isoMatcher.group(2)!!.toInt() - 1
                val d = isoMatcher.group(3)!!.toInt()
                val cal = Calendar.getInstance()
                cal.set(y, m, d)
                timestamp = cal.timeInMillis
            } catch (_: Exception) {}
        } else if (localMatcher.find()) {
            try {
                val d = localMatcher.group(1)!!.toInt()
                val m = localMatcher.group(2)!!.toInt() - 1
                val y = localMatcher.group(3)!!.toInt()
                if (m in 0..11 && d in 1..31) {
                    val cal = Calendar.getInstance()
                    cal.set(y, m, d)
                    timestamp = cal.timeInMillis
                }
            } catch (_: Exception) {}
        }

        // 8. Suggest Category
        val mLower = (merchant ?: "").lowercase(Locale.ROOT)
        val suggestedCat = when {
            transactionType == "BILL_PAYMENT" || mLower.contains("ecg") || mLower.contains("water") || mLower.contains("dstv") || mLower.contains("power") -> "Bills & Utilities"
            mLower.contains("kfc") || mLower.contains("chop") || mLower.contains("restaurant") || mLower.contains("inn") || mLower.contains("pizza") || mLower.contains("buka") || mLower.contains("papaye") || mLower.contains("burger") || mLower.contains("starbites") -> "Food & Dining"
            mLower.contains("shell") || mLower.contains("total") || mLower.contains("goil") || mLower.contains("fuel") || mLower.contains("bolt") || mLower.contains("uber") || mLower.contains("yango") -> "Transportation"
            mLower.contains("shoprite") || mLower.contains("melcom") || mLower.contains("game") || mLower.contains("palace") || mLower.contains("market") || mLower.contains("supermarket") || mLower.contains("mart") -> "Shopping & Groceries"
            else -> "General"
        }

        // 9. Payment Rail
        val paymentRail = when {
            lowerText.contains("momo") || lowerText.contains("mtn") -> "MTN MoMo"
            lowerText.contains("telecel") || lowerText.contains("vodafone") -> "Telecel Cash"
            lowerText.contains("visa") || lowerText.contains("mastercard") || lowerText.contains("card") || lowerText.contains("pos") -> "Card"
            lowerText.contains("bank") || lowerText.contains("transfer") -> "Bank"
            lowerText.contains("cash") -> "Cash"
            else -> null
        }

        // 10. Confidence Score
        var confidence = 0.5
        if (total != null && total > 0) confidence += 0.2
        if (!merchant.isNullOrBlank() && merchant != "Scanned Merchant") confidence += 0.2
        if (!referenceNumber.isNullOrBlank()) confidence += 0.1

        return ReceiptDraft(
            amount = total,
            merchant = merchant ?: "Scanned Merchant",
            date = timestamp,
            tax = tax,
            confidenceScore = confidence.coerceIn(0.0, 1.0),
            currency = currency,
            referenceNumber = referenceNumber,
            transactionType = transactionType,
            suggestedCategory = suggestedCat,
            paymentRail = paymentRail,
            rawText = rawText,
            isAiEnhanced = false
        )
    }

    private fun toTitleCase(input: String): String {
        return input.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
            }
    }
}
