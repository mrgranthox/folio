package com.example.data.engine

import com.example.data.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmsParserEngineTest {

    private lateinit var parser: SmsParserEngine

    @Before
    fun setUp() {
        parser = SmsParserEngine()
    }

    @Test
    fun parseMtnMoMoPayment() {
        val sms = "Payment made for GHS 120.00 to KFC GHANA. Current Balance: GHS 450.50. Reference: 1827364529. Fee was GHS 1.20."
        val result = parser.parse("MobileMoney", sms)

        assertTrue("Should be financial", result.isFinancial)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(120.0, result.amount ?: 0.0, 0.01)
        assertEquals(450.50, result.endingBalance ?: 0.0, 0.01)
        assertEquals(1.20, result.fee ?: 0.0, 0.01)
        assertTrue(result.counterparty.contains("KFC", ignoreCase = true))
    }

    @Test
    fun parseMtnCashInCredit() {
        val sms = "Cash In received for GHS 500.00 from KOKOMLEMLE AGENT. Current Balance: GHS 950.50. Reference: 9918273645."
        val result = parser.parse("MobileMoney", sms)

        assertTrue(result.isFinancial)
        assertEquals(TransactionDirection.CREDIT, result.direction)
        assertEquals(500.0, result.amount ?: 0.0, 0.01)
        assertEquals(950.50, result.endingBalance ?: 0.0, 0.01)
    }

    @Test
    fun parseTelecelCashTransfer() {
        val sms = "You have transferred GHS 85.00 to AMA BOATENG. Your Telecel Cash balance is GHS 210.00. Trans ID: TC827364."
        val result = parser.parse("Telecel", sms)

        assertTrue(result.isFinancial)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(85.0, result.amount ?: 0.0, 0.01)
        assertEquals(210.0, result.endingBalance ?: 0.0, 0.01)
        assertTrue(result.counterparty.contains("AMA BOATENG", ignoreCase = true))
    }

    @Test
    fun parseBankDebitAlert() {
        val sms = "Debit Alert! Acct: **4321 Amt: GHS 350.00 Desc: POS PURCHASE - SHELL AIRPORT Avail Bal: GHS 4,200.00 Ref: TXN998811"
        val result = parser.parse("GCB", sms)

        assertTrue(result.isFinancial)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(350.0, result.amount ?: 0.0, 0.01)
    }

    @Test
    fun parseNonFinancialSpam() {
        val sms = "Win 100x bonus! Dial *550# now to subscribe to MTN Mashup. Terms and conditions apply."
        val result = parser.parse("MTN Promo", sms)

        assertFalse("Promotional messages should not be marked as financial", result.isFinancial)
        assertTrue(result.isPromotional)
    }

    @Test
    fun parsePromotionalAdWithMonetaryAmountBlocked() {
        val sms = "Win GHS 500 in the MoMo promo! Dial *170# now to play. Terms and conditions apply."
        val result = parser.parse("MobileMoney", sms)

        assertFalse("Promotional adverts with monetary amounts must be rejected", result.isFinancial)
        assertTrue(result.isPromotional)
    }

    @Test
    fun parseThirdPartyMarketingBureauBlocked() {
        val sms = "paying 70 cedis out of for a certificate at our office. Call 0244000000."
        val result = parser.parse("TransAfrica Bureau", sms)

        assertFalse("Third party bureau messages without transaction proof must be rejected", result.isFinancial)
    }

    @Test
    fun parseBankCashbackMarketingBlocked() {
        val sms = "Enjoy GHS 50 cashback when you use your Ecobank card this weekend! T&Cs apply."
        val result = parser.parse("Ecobank", sms)

        assertFalse("Bank marketing solicitations must be rejected", result.isFinancial)
        assertTrue(result.isPromotional)
    }

    @Test
    fun parseBankMaintenanceNoticeBlocked() {
        val sms = "Dear Customer, please note that Ecobank systems will undergo scheduled maintenance this Sunday."
        val result = parser.parse("Ecobank", sms)

        assertFalse("Bank service maintenance notices must be rejected", result.isFinancial)
    }

    @Test
    fun parseLoanSolicitationBlocked() {
        val sms = "Qualify for an instant loan of GHS 1,500. Apply now via *170#."
        val result = parser.parse("MobileMoney", sms)

        assertFalse("Loan solicitations without execution proof must be rejected", result.isFinancial)
    }
}
