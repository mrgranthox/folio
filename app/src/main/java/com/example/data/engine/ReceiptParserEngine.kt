package com.example.data.engine

import java.util.Calendar
import java.util.regex.Pattern

data class ReceiptDraft(
    val amount: Double? = null,
    val merchant: String? = null,
    val date: Long = System.currentTimeMillis(),
    val tax: Double? = null,
    val confidenceScore: Double = 0.5,
    val currency: String = "GHS"
)

class ReceiptParserEngine {

    fun parse(rawText: String): ReceiptDraft {
        val lines = rawText.split(Regex("\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 1. Detect Currency (Default to GHS)
        var currency = "GHS"
        if (rawText.contains("USD") || rawText.contains("$")) {
            currency = "USD"
        } else if (rawText.contains("EUR") || rawText.contains("€")) {
            currency = "EUR"
        } else if (rawText.contains("GBP") || rawText.contains("£")) {
            currency = "GBP"
        }

        // 2. Extract Grand Total
        var total: Double? = null
        val explicitTotalPattern = Pattern.compile(
            """(?:grand\s+total|total\s+amount|net\s+total|total)[^\d]{0,14}(?:GHS|GH[Cc¢₵\u20B5\u01B5]|\$|€|£)?\s*(\d{1,7}[.,]\d{2})""",
            Pattern.CASE_INSENSITIVE
        )
        val explicitTotalMatcher = explicitTotalPattern.matcher(rawText)
        if (explicitTotalMatcher.find()) {
            val amtStr = explicitTotalMatcher.group(1)?.replace(",", ".")
            total = amtStr?.toDoubleOrNull()
        }

        if (total == null) {
            val amountPattern = Pattern.compile("""(?:^|\s)(?:GHS|GH[Cc¢₵\u20B5\u01B5]|\$|€|£)?\s*(\d{1,7}[.,]\d{2})(?=\s|$)""")
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
            """(?:tax|vat|nhil|getfund|levy)[^\d]{0,12}(?:GHS|GH[Cc¢₵\u20B5\u01B5]|\$|€|£)?\s*(\d{1,5}[.,]\d{2})""",
            Pattern.CASE_INSENSITIVE
        )
        val taxMatcher = taxPattern.matcher(rawText)
        if (taxMatcher.find()) {
            val taxStr = taxMatcher.group(1)?.replace(",", ".")
            tax = taxStr?.toDoubleOrNull()
        }

        // 4. Extract Merchant Name
        var merchant: String? = null
        for (line in lines) {
            val hasLetters = line.any { it.isLetter() }
            val isShort = line.length in 3..50
            val isGeneric = line.matches(
                Regex("(?i).*(receipt|invoice|thank|welcome|total|date|time|tel|www|tax|cashier|terminal|customer|vat\\s*no).*")
            )
            val digitCount = line.count { it.isDigit() }
            if (hasLetters && isShort && !isGeneric && digitCount < line.length / 2) {
                merchant = line
                break
            }
        }

        // 5. Extract Date
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

        // 6. Confidence Score
        var confidence = 0.5
        if (total != null && total > 0) confidence += 0.3
        if (!merchant.isNullOrBlank()) confidence += 0.2

        return ReceiptDraft(
            amount = total,
            merchant = merchant ?: "Scanned Merchant",
            date = timestamp,
            tax = tax,
            confidenceScore = confidence.coerceIn(0.0, 1.0),
            currency = currency
        )
    }
}
