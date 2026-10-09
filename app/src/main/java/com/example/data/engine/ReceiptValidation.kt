package com.example.data.engine

import kotlin.math.abs

object ReceiptValidation {
    private val supportedCurrencies = setOf("GHS", "USD", "EUR", "GBP", "NGN", "KES", "ZAR")

    /** Produces a deterministic ceiling; model self-confidence can never exceed this value. */
    fun confidenceCeiling(draft: ReceiptDraft, now: Long = System.currentTimeMillis()): Double {
        var score = 0.20
        if (draft.amount != null && draft.amount.isFinite() && draft.amount > 0.0 && draft.amount < 100_000_000.0) score += 0.35
        if (!draft.merchant.isNullOrBlank() && draft.merchant != "Scanned Merchant" && draft.merchant.length >= 2) score += 0.20
        if (!draft.referenceNumber.isNullOrBlank() && draft.referenceNumber.length in 4..64) score += 0.10
        if (draft.currency.uppercase() in supportedCurrencies) score += 0.05
        val fiveYears = 5L * 366 * 24 * 60 * 60 * 1000
        if (draft.date in (now - fiveYears)..(now + 24 * 60 * 60 * 1000)) score += 0.10
        if (draft.tax != null && draft.amount != null && (draft.tax < 0 || draft.tax > draft.amount)) score -= 0.20
        return score.coerceIn(0.0, 1.0)
    }

    fun isTotalConsistent(subtotal: Double?, tax: Double?, fees: Double?, total: Double?, tolerance: Double = 0.02): Boolean {
        if (subtotal == null || total == null) return true
        val expected = subtotal + (tax ?: 0.0) + (fees ?: 0.0)
        return abs(expected - total) <= maxOf(tolerance, total * 0.01)
    }
}
