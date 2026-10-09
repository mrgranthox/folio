package com.example.data.engine

import com.example.data.model.TransactionDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestionPolicyTest {
    private fun parsed(confidence: Double = 0.98, ref: String? = "TX123456", merchant: String = "KFC") =
        ParsedSmsResult(
            isFinancial = true,
            externalRef = ref,
            amount = 25.0,
            direction = TransactionDirection.DEBIT,
            counterparty = merchant,
            rawBody = "Payment made for GHS 25.00",
            provider = "MTN MoMo",
            confidenceScore = confidence
        )

    @Test fun untrustedAutomaticInputNeverPosts() {
        assertFalse(IngestionPolicy.evaluateSms(parsed(), trustedTransport = false, userConfirmed = false).mayPost)
    }

    @Test fun explicitUserConfirmationMayPost() {
        assertTrue(IngestionPolicy.evaluateSms(parsed(0.85, ref = null), trustedTransport = false, userConfirmed = true).mayPost)
    }

    @Test fun automaticPostingRequiresReferenceAndHighConfidence() {
        assertFalse(IngestionPolicy.evaluateSms(parsed(ref = null), true, false).mayPost)
        assertFalse(IngestionPolicy.evaluateSms(parsed(confidence = 0.97), true, false).mayPost)
        assertTrue(IngestionPolicy.evaluateSms(parsed(), true, false).mayPost)
    }

    @Test fun sourceTimestampOverridesParserClock() {
        val timestamp = 1_700_000_000_000L
        val result = SmsParserEngine().parse(
            "MobileMoney",
            "Payment made for GHS 25.00 to KFC. Current Balance: GHS 100.00. Financial Transaction Id: ABC12345.",
            sourceTimestamp = timestamp
        )
        assertTrue(result.isFinancial)
        assertTrue(result.timestamp == timestamp)
    }

    @Test fun unattendedUnknownSenderCannotSelfAuthorizeFromBody() {
        val body = "Payment made for GHS 25.00 to KFC. Current Balance: GHS 100.00. Financial Transaction Id: ABC12345."
        val result = SmsParserEngine().parse("Unknown", body, allowBodySenderInference = false)
        assertFalse(result.isFinancial)
    }
}
