package com.example.data.engine

/** Central policy for deciding whether machine-extracted data may affect the ledger. */
object IngestionPolicy {
    const val AUTO_POST_MIN_CONFIDENCE = 0.98

    data class Decision(val mayPost: Boolean, val reason: String)

    fun evaluateSms(parsed: ParsedSmsResult, trustedTransport: Boolean, userConfirmed: Boolean): Decision {
        if (!parsed.isFinancial) return Decision(false, parsed.rejectionReason ?: "Not a financial transaction")
        if (userConfirmed) return Decision(true, "Confirmed by user")
        if (!trustedTransport) return Decision(false, "Untrusted message source")
        if (parsed.amount == null || parsed.amount <= 0.0) return Decision(false, "Amount is missing")
        if (parsed.externalRef.isNullOrBlank()) return Decision(false, "Transaction reference is missing")
        if (parsed.confidenceScore < AUTO_POST_MIN_CONFIDENCE) return Decision(false, "Extraction confidence is below the auto-post threshold")
        if (parsed.counterparty == "Unknown Payee") return Decision(false, "Counterparty is uncertain")
        return Decision(true, "Known provider template with high-confidence critical fields")
    }
}
