package com.example.data.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptValidationTest {
    @Test fun incompleteAiResultCannotClaimHighConfidence() {
        val draft = ReceiptDraft(amount = 12.0, merchant = "Scanned Merchant", confidenceScore = 0.99)
        assertTrue(ReceiptValidation.confidenceCeiling(draft) < 0.90)
    }

    @Test fun impossibleTaxLowersConfidence() {
        val normal = ReceiptDraft(amount = 100.0, merchant = "Shop", tax = 10.0, referenceNumber = "ABC123")
        val impossible = normal.copy(tax = 150.0)
        assertTrue(ReceiptValidation.confidenceCeiling(impossible) < ReceiptValidation.confidenceCeiling(normal))
    }

    @Test fun arithmeticValidationUsesTolerance() {
        assertTrue(ReceiptValidation.isTotalConsistent(100.0, 5.0, 1.0, 106.0))
        assertFalse(ReceiptValidation.isTotalConsistent(100.0, 5.0, 1.0, 120.0))
    }
}
