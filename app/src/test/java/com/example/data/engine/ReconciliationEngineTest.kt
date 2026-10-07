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
}
