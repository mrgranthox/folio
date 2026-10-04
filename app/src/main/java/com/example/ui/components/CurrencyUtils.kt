package com.example.ui.components

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

object CurrencyUtils {

    private val decimalFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    fun getCurrencySymbol(code: String?): String {
        return when (code?.uppercase()?.trim()) {
            "GHS", "GH₵", "GHC" -> "GH₵"
            "USD", "$" -> "$"
            "EUR", "€" -> "€"
            "GBP", "£" -> "£"
            "NGN", "₦" -> "₦"
            "KES" -> "KSh"
            else -> code ?: "GH₵"
        }
    }

    fun format(amount: Double, currency: String = "GHS", showSign: Boolean = false): String {
        val symbol = getCurrencySymbol(currency)
        val formattedNumber = decimalFormat.format(abs(amount))
        return if (showSign) {
            val sign = if (amount > 0) "+" else if (amount < 0) "-" else ""
            "$sign $symbol $formattedNumber"
        } else {
            "$symbol $formattedNumber"
        }
    }
}
