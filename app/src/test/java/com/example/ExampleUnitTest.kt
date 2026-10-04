package com.example

import com.example.data.engine.ReceiptParserEngine
import com.example.data.engine.ReconciliationEngine
import com.example.data.engine.ReconciliationOutcome
import com.example.data.engine.SmsParserEngine
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testSmsParserMoMoDebit() {
        val parser = SmsParserEngine()
        val raw = "Payment made for GHS 120.00 to KFC ACCRA AIRPORT. Current Balance: GHS 4,130.00. Reference: 8819201948. Financial Transaction Id: 98127391."
        val result = parser.parse("MobileMoney", raw)

        assertTrue(result.isFinancial)
        assertEquals(120.00, result.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals("98127391", result.externalRef)
        assertEquals("KFC ACCRA AIRPORT", result.counterparty)
        assertEquals(4130.00, result.endingBalance!!, 0.001)
    }

    @Test
    fun testSmsParserMoMoCredit() {
        val parser = SmsParserEngine()
        val raw = "You have received GHS 3,500.00 from KWAME APPIAH. Current Balance: GHS 7,630.00. Reference: Retainer. Financial Transaction Id: 99482103."
        val result = parser.parse("MobileMoney", raw)

        assertTrue(result.isFinancial)
        assertEquals(3500.00, result.amount!!, 0.001)
        assertEquals(TransactionDirection.CREDIT, result.direction)
        assertEquals("99482103", result.externalRef)
        assertEquals("KWAME APPIAH", result.counterparty)
    }

    @Test
    fun testReconciliationDeterministicDuplicate() {
        val reconciler = ReconciliationEngine()
        val existing = listOf(
            TransactionEntity(
                id = "tx-1",
                externalRef = "REF-9999",
                accountId = "acc-1",
                categoryId = "cat-1",
                amount = -50.0,
                counterparty = "Bolt Ride"
            )
        )
        val incoming = TransactionEntity(
            id = "tx-2",
            externalRef = "REF-9999",
            accountId = "acc-1",
            categoryId = "cat-1",
            amount = -50.0,
            counterparty = "Bolt Ride"
        )

        val outcome = reconciler.evaluate(incoming, existing)
        assertEquals(ReconciliationOutcome.IDEMPOTENT_SKIP, outcome.outcome)
    }

    @Test
    fun testReceiptParser() {
        val parser = ReceiptParserEngine()
        val raw = """
            MAXMART SUPERMARKET ACCRA
            DATE: 2026-09-24
            TOTAL: GHS 720.60
            THANK YOU
        """.trimIndent()
        val draft = parser.parse(raw)

        assertEquals(720.60, draft.amount!!, 0.001)
        assertEquals("MAXMART SUPERMARKET ACCRA", draft.merchant)
    }
}
