package com.example.data.engine

import com.example.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ReconciliationEngineTest {

    private lateinit var reconciler: ReconciliationEngine

    @Before
    fun setUp() {
        reconciler = ReconciliationEngine()
    }

    @Test
    fun hardMatchByExternalRefSkipsDuplicate() {
        val existing = listOf(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                externalRef = "REF12345",
                accountId = "acc-1",
                categoryId = "cat-1",
                amount = -50.0,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = System.currentTimeMillis() - 60000,
                counterparty = "Starbites"
            )
        )

        val incoming = TransactionEntity(
            id = UUID.randomUUID().toString(),
            externalRef = "REF12345",
            accountId = "acc-1",
            categoryId = "cat-1",
            amount = -50.0,
            currency = "GHS",
            direction = "DEBIT",
            type = "Expense",
            timestamp = System.currentTimeMillis(),
            counterparty = "Starbites Westlands"
        )

        val result = reconciler.evaluate(incoming, existing)
        assertEquals(ReconciliationOutcome.IDEMPOTENT_SKIP, result.outcome)
        assertEquals(100, result.matchScore)
    }

    @Test
    fun fuzzyMatchAutoMergesOcrAndSms() {
        val now = System.currentTimeMillis()
        val manualOcr = TransactionEntity(
            id = UUID.randomUUID().toString(),
            externalRef = null,
            accountId = "acc-1",
            categoryId = "cat-1",
            amount = -75.0,
            currency = "GHS",
            direction = "DEBIT",
            type = "Expense",
            timestamp = now - 5000, // 5 seconds ago
            counterparty = "Total Energies",
            sourceMethod = "ocr"
        )

        val incomingSms = TransactionEntity(
            id = UUID.randomUUID().toString(),
            externalRef = "SMS991122",
            accountId = "acc-1",
            categoryId = "cat-1",
            amount = -75.0,
            currency = "GHS",
            direction = "DEBIT",
            type = "Expense",
            timestamp = now,
            counterparty = "Total Energies Legon",
            sourceMethod = "sms"
        )

        val result = reconciler.evaluate(incomingSms, listOf(manualOcr))
        assertTrue(result.outcome == ReconciliationOutcome.AUTO_MERGED || result.outcome == ReconciliationOutcome.QUEUED_FOR_REVIEW)
        assertTrue(result.matchScore >= 60)
    }

    @Test
    fun completelyDistinctTransactionInsertsNew() {
        val existing = listOf(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                externalRef = "REF111",
                accountId = "acc-1",
                categoryId = "cat-1",
                amount = -20.0,
                currency = "GHS",
                direction = "DEBIT",
                type = "Expense",
                timestamp = System.currentTimeMillis() - 86400000,
                counterparty = "Shoprite"
            )
        )

        val incoming = TransactionEntity(
            id = UUID.randomUUID().toString(),
            externalRef = "REF222",
            accountId = "acc-2",
            categoryId = "cat-2",
            amount = -450.0,
            currency = "GHS",
            direction = "DEBIT",
            type = "Expense",
            timestamp = System.currentTimeMillis(),
            counterparty = "CompuGhana"
        )

        val result = reconciler.evaluate(incoming, existing)
        assertEquals(ReconciliationOutcome.INSERTED_NEW, result.outcome)
    }

    @Test
    fun crossRailTransferFromMoMoToEcobankSkipsDuplicate() {
        val now = System.currentTimeMillis()
        val momoLeg = TransactionEntity(
            id = UUID.randomUUID().toString(),
            externalRef = "MOMO123456",
            accountId = "acc-momo",
            categoryId = "cat-transfer",
            amount = -200.0,
            currency = "GHS",
            direction = "DEBIT",
            type = "Transfer",
            timestamp = now - 60000, // 1 minute ago
            counterparty = "ECOBANK GHANA LTD",
            accountRail = "MTN MoMo",
            notes = "Transfer to Ecobank account"
        )

        val ecobankLeg = TransactionEntity(
            id = UUID.randomUUID().toString(),
            externalRef = "ECO789012",
            accountId = "acc-bank",
            categoryId = "cat-transfer",
            amount = -200.0,
            currency = "GHS",
            direction = "DEBIT",
            type = "Transfer",
            timestamp = now,
            counterparty = "MTN MOMO TRANSFER",
            accountRail = "Ecobank",
            notes = "Automated ingest via Ecobank"
        )

        val result = reconciler.evaluate(ecobankLeg, listOf(momoLeg))
        assertEquals(ReconciliationOutcome.IDEMPOTENT_SKIP, result.outcome)
        assertEquals(100, result.matchScore)
        assertTrue(result.matchFactors.first().contains("Cross-rail transfer duplicate detected"))
    }

    @Test
    fun sameRailDuplicateTransmissionWithinTenMinutesSkipsDuplicate() {
        val now = System.currentTimeMillis()
        val originalTx = TransactionEntity(
            id = UUID.randomUUID().toString(),
            externalRef = null,
            accountId = "acc-momo",
            categoryId = "cat-food",
            amount = -75.0,
            currency = "GHS",
            direction = "DEBIT",
            type = "Expense",
            timestamp = now - 120000, // 2 minutes ago
            counterparty = "KFC AIRPORT ACCRA",
            accountRail = "MTN MoMo"
        )

        val duplicateSmsTx = TransactionEntity(
            id = UUID.randomUUID().toString(),
            externalRef = null,
            accountId = "acc-momo",
            categoryId = "cat-food",
            amount = -75.0,
            currency = "GHS",
            direction = "DEBIT",
            type = "Expense",
            timestamp = now,
            counterparty = "KFC AIRPORT ACCRA",
            accountRail = "MTN MoMo"
        )

        val result = reconciler.evaluate(duplicateSmsTx, listOf(originalTx))
        assertEquals(ReconciliationOutcome.IDEMPOTENT_SKIP, result.outcome)
        assertEquals(100, result.matchScore)
        assertTrue(result.matchFactors.first().contains("Duplicate SMS retransmission detected"))
    }
}
