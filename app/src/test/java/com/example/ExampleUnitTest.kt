package com.example

import com.example.data.engine.ReceiptParserEngine
import com.example.data.engine.ReconciliationEngine
import com.example.data.engine.ReconciliationOutcome
import com.example.data.engine.SmsParserEngine
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun testSmsParserEcgMeterRecharge() {
        val parser = SmsParserEngine()
        val raw = "A recharge request of GHS 25.00 to meter 54310900789 (STEPHEN ETSE) has been processed successfully. Use this token (69579079454110730781) to recharge your meter. Txn ID: EAD00605119"
        val result = parser.parse("ECG", raw)

        // ECG transactions are non-financial utility receipts (paid via MoMo or Bank) and not tracked directly
        assertFalse(result.isFinancial)
    }

    @Test
    fun testSmsParserBankDebitAlert() {
        val parser = SmsParserEngine()
        val raw = "Debit: Acct: **4321 Amt: GHS 250.00 Desc: POS PURCHASE - SHOPRITE ACCRA Date: 04-OCT-2026 Bal: GHS 1,450.00 Ref: ECO-883921"
        val result = parser.parse("Ecobank", raw)

        assertTrue(result.isFinancial)
        assertEquals(250.00, result.amount!!, 0.001)
        assertEquals("SHOPRITE ACCRA", result.counterparty)
        assertEquals("ECO-883921", result.externalRef)
        assertEquals(1450.00, result.endingBalance!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, result.direction)
    }

    @Test
    fun testSmsParserMoMoP2PWithPhone() {
        val parser = SmsParserEngine()
        val raw = "Payment made for GHS 150.00 to 0244123456 - KOFI MENSAH. Current Balance: GHS 956.81. Available Balance: GHS 956.81. Fee charged: GHS 1.12. Reference: Support. Financial Transaction Id: 42475490996."
        val result = parser.parse("MobileMoney", raw)

        assertTrue(result.isFinancial)
        assertEquals(150.00, result.amount!!, 0.001)
        assertEquals("KOFI MENSAH", result.counterparty)
        assertEquals("0244123456", result.counterpartyPhone)
        assertEquals("42475490996", result.externalRef)
        assertEquals(1.12, result.fee!!, 0.001)
        assertEquals(956.81, result.endingBalance!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, result.direction)
    }

    @Test
    fun testSmsParserMoMoCashOutAgent() {
        val parser = SmsParserEngine()
        val raw = "Cash Out of GHS 200.00 from Agent 0244111222 - JOE VENTURES was successful. Fee charged: GHS 2.00. Current Balance: GHS 1,998.12. Available Balance: GHS 1,998.12. Reference: Cash out. Transaction ID: 42483562773."
        val result = parser.parse("MobileMoney", raw)

        assertTrue(result.isFinancial)
        assertEquals(200.00, result.amount!!, 0.001)
        assertEquals("JOE VENTURES (MoMo Agent)", result.counterparty)
        assertEquals("0244111222", result.counterpartyPhone)
        assertEquals("42483562773", result.externalRef)
        assertEquals(2.00, result.fee!!, 0.001)
        assertEquals(1998.12, result.endingBalance!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, result.direction)
    }

    @Test
    fun testSmsParserTelecelCash() {
        val parser = SmsParserEngine()
        val raw = "Paid GHS 45.00 to MELCOM PLUS on 04/10/2026. Bal: GHS 134.20. Ref: 20261004123456. Trans ID: TC-849201."
        val result = parser.parse("T-CASH", raw)

        assertTrue(result.isFinancial)
        assertEquals(45.00, result.amount!!, 0.001)
        assertEquals("MELCOM PLUS", result.counterparty)
        assertEquals("TC-849201", result.externalRef)
        assertEquals(134.20, result.endingBalance!!, 0.001)
        assertEquals("Telecel Cash", result.provider)
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
        assertEquals(ReconciliationOutcome.QUEUED_FOR_REVIEW, outcome.outcome)
    }

    @Test
    fun testReceiptParser() {
        val parser = ReceiptParserEngine()
        val raw = """
            MAXMART SUPERMARKET ACCRA
            DATE: 2026-09-24
            RECEIPT NO: RCP-883921
            TOTAL: GHS 720.60
            THANK YOU
        """.trimIndent()
        val draft = parser.parse(raw)

        assertEquals(720.60, draft.amount!!, 0.001)
        assertEquals("Maxmart Supermarket Accra", draft.merchant)
        assertEquals("RCP-883921", draft.referenceNumber)
        assertEquals("EXPENSE", draft.transactionType)
    }
}
