package com.example.data.engine

import com.example.data.model.DuplicateEntity
import com.example.data.model.TransactionEntity
import java.util.UUID
import kotlin.math.abs

enum class ReconciliationOutcome {
    IDEMPOTENT_SKIP, // Deterministic duplicate (Tier 1 externalRef match)
    AUTO_MERGED,     // Fuzzy Tier 2 match (Score >= 80)
    QUEUED_FOR_REVIEW,// Fuzzy Tier 2 match (Score 60-79)
    INSERTED_NEW     // Score < 60
}

data class ReconciliationResult(
    val outcome: ReconciliationOutcome,
    val resolvedTransaction: TransactionEntity? = null,
    val candidateForQueue: DuplicateEntity? = null,
    val matchScore: Int = 0,
    val matchFactors: List<String> = emptyList()
)

class ReconciliationEngine {

    private fun isTransactionIdMatch(ref1: String?, ref2: String?): Boolean {
        if (ref1.isNullOrBlank() || ref2.isNullOrBlank()) return false
        val clean1 = ref1.trim().trim('.', ',', ';', ':', '-', ' ').lowercase()
        val clean2 = ref2.trim().trim('.', ',', ';', ':', '-', ' ').lowercase()
        if (clean1 == clean2) return true

        // Clean out common prefixes like "tnx:", "txn:", "ref:"
        val stripped1 = clean1.replace(Regex("^(?:tnx|txn|ref|trx|transaction|trans)\\s*[:.]?\\s*"), "")
        val stripped2 = clean2.replace(Regex("^(?:tnx|txn|ref|trx|transaction|trans)\\s*[:.]?\\s*"), "")
        if (stripped1 == stripped2 && stripped1.length >= 4) return true

        if (clean1.length >= 6 && clean2.length >= 6) {
            if (clean1.contains(clean2) || clean2.contains(clean1)) return true
            val digits1 = clean1.filter { it.isDigit() }
            val digits2 = clean2.filter { it.isDigit() }
            if (digits1.length >= 6 && digits2.length >= 6 && digits1 == digits2) return true
        }

        return false
    }

    fun evaluate(
        incoming: TransactionEntity,
        existingTransactions: List<TransactionEntity>
    ): ReconciliationResult {
        // ------------------------------------------------------------------------
        // TIER 1: Transaction ID / Reference ID Hard Matching (TNX ID, Transaction ID)
        // Strictly looks at transaction ID / reference ID, ignoring differences in amount.
        // ------------------------------------------------------------------------
        if (!incoming.externalRef.isNullOrBlank()) {
            val incomingRef = incoming.externalRef!!
            val hardMatch = existingTransactions.firstOrNull { t ->
                if (t.isDeleted) return@firstOrNull false
                if (isTransactionIdMatch(incomingRef, t.externalRef)) return@firstOrNull true
                if (!t.notes.isNullOrBlank() && isTransactionIdMatch(incomingRef, t.notes)) return@firstOrNull true
                false
            }
            if (hardMatch != null) {
                val candidate = DuplicateEntity(
                    id = "dup-${UUID.randomUUID()}",
                    existingTransactionId = hardMatch.id,
                    importedExternalRef = incoming.externalRef,
                    importedAccountId = incoming.accountId,
                    importedCategoryId = incoming.categoryId,
                    importedAmount = incoming.amount,
                    importedCurrency = incoming.currency,
                    importedDirection = incoming.direction,
                    importedType = incoming.type,
                    importedTimestamp = incoming.timestamp,
                    importedCounterparty = incoming.counterparty,
                    importedSourceMethod = incoming.sourceMethod,
                    importedNotes = incoming.notes,
                    importedReceiptImagePath = incoming.receiptImagePath,
                    importedAccountRail = incoming.accountRail,
                    matchScore = 100,
                    matchFactors = "Identical Transaction ID: ${incoming.externalRef}"
                )
                return ReconciliationResult(
                    outcome = ReconciliationOutcome.QUEUED_FOR_REVIEW,
                    candidateForQueue = candidate,
                    matchScore = 100,
                    matchFactors = listOf("Identical Transaction ID: ${incoming.externalRef}")
                )
            }
        }

        // ------------------------------------------------------------------------
        // TIER 1B: Same-Rail Duplicate Transmission (Network Resend within 10 minutes)
        // ------------------------------------------------------------------------
        val sameRailDup = existingTransactions.firstOrNull { t ->
            if (t.isDeleted) return@firstOrNull false
            val amountMatch = abs(abs(t.amount) - abs(incoming.amount)) < 0.01
            val directionMatch = t.direction == incoming.direction
            val railMatch = !incoming.accountRail.isNullOrBlank() &&
                    !t.accountRail.isNullOrBlank() &&
                    incoming.accountRail.equals(t.accountRail, ignoreCase = true)
            val timeDiffMinutes = abs(incoming.timestamp - t.timestamp).toDouble() / (1000 * 60)
            if (amountMatch && directionMatch && railMatch && timeDiffMinutes <= 10.0) {
                val normInc = normalizeMerchant(incoming.counterparty)
                val normEx = normalizeMerchant(t.counterparty)
                normInc == normEx || normInc.contains(normEx) || normEx.contains(normInc) ||
                        calculateJaccardSimilarity(normInc, normEx) >= 0.7
            } else false
        }
        if (sameRailDup != null) {
            return ReconciliationResult(
                outcome = ReconciliationOutcome.IDEMPOTENT_SKIP,
                matchScore = 100,
                matchFactors = listOf(
                    "Duplicate SMS retransmission detected on ${incoming.accountRail} within 10 mins (${incoming.counterparty})"
                )
            )
        }

        // ------------------------------------------------------------------------
        // TIER 1C: Cross-Rail Transfer Duplicate (e.g. MTN MoMo <-> Bank e.g. Ecobank)
        // ------------------------------------------------------------------------
        val crossRailDup = existingTransactions.firstOrNull { t ->
            if (t.isDeleted) return@firstOrNull false
            val amountMatch = abs(abs(t.amount) - abs(incoming.amount)) < 0.02
            val timeDiffMinutes = abs(incoming.timestamp - t.timestamp).toDouble() / (1000 * 60)
            if (!amountMatch || timeDiffMinutes > 15.0) return@firstOrNull false

            val incRail = incoming.accountRail ?: ""
            val exRail = t.accountRail ?: ""

            val incIsMoMo = isMoMoRail(incRail)
            val exIsMoMo = isMoMoRail(exRail)
            val incIsBank = isBankRail(incRail)
            val exIsBank = isBankRail(exRail)

            val isCrossPair = (incIsMoMo && exIsBank) || (incIsBank && exIsMoMo)
            if (!isCrossPair) return@firstOrNull false

            val momoTx = if (incIsMoMo) incoming else t
            val bankTx = if (incIsBank) incoming else t

            val momoText = "${momoTx.counterparty} ${momoTx.notes ?: ""}".lowercase()
            val bankText = "${bankTx.counterparty} ${bankTx.notes ?: ""} ${bankTx.accountRail ?: ""}".lowercase()

            val bankNames = listOf(
                "ecobank", "stanbic", "gcb", "gtbank", "zenith", "absa", "fidelity",
                "calbank", "access", "standard chartered", "fnb", "cbg", "bank"
            )
            val momoKeywords = listOf(
                "momo", "mtn", "mobile money", "telecel", "vodafone", "t-cash", "at money", "wallet"
            )

            val momoMentionsBank = bankNames.any { momoText.contains(it) }
            val bankMentionsMoMo = momoKeywords.any { bankText.contains(it) }

            momoMentionsBank || bankMentionsMoMo
        }
        if (crossRailDup != null) {
            return ReconciliationResult(
                outcome = ReconciliationOutcome.IDEMPOTENT_SKIP,
                matchScore = 100,
                matchFactors = listOf(
                    "Cross-rail transfer duplicate detected between ${incoming.accountRail} and ${crossRailDup.accountRail} for ${incoming.currency} ${abs(incoming.amount)}"
                )
            )
        }

        // ------------------------------------------------------------------------
        // TIER 2: Probabilistic Reconciler (Fuzzy Matching)
        // ------------------------------------------------------------------------
        var highestScore = 0
        var bestMatch: TransactionEntity? = null
        var bestFactors = emptyList<String>()

        for (existing in existingTransactions) {
            if (existing.isDeleted) continue

            // Direction must match! (Credit cannot merge with Debit)
            if (existing.direction != incoming.direction) continue

            var score = 0
            val currentFactors = mutableListOf<String>()

            // 1. Amount match (40 points)
            val incomingAmount = abs(incoming.amount)
            val existingAmount = abs(existing.amount)

            if (abs(incomingAmount - existingAmount) < 0.01) {
                score += 40
                currentFactors.add("Exact amount match (${incoming.currency} ${String.format("%.2f", incomingAmount)})")
            } else if (existingAmount > 0 && abs(incomingAmount - existingAmount) / existingAmount < 0.05) {
                score += 25
                currentFactors.add("Near amount match (±5%)")
            }

            // 2. Account Rail match (15 points)
            if (!incoming.accountRail.isNullOrBlank() &&
                !existing.accountRail.isNullOrBlank() &&
                incoming.accountRail.equals(existing.accountRail, ignoreCase = true)
            ) {
                score += 15
                currentFactors.add("Same account rail (${incoming.accountRail})")
            }

            // 3. Time Bucket match (25 points)
            val timeDiffHours = abs(incoming.timestamp - existing.timestamp).toDouble() / (1000 * 60 * 60)
            if (timeDiffHours <= 0.5) {
                score += 25
                currentFactors.add("Within 30 minutes")
            } else if (timeDiffHours <= 2.0) {
                score += 20
                currentFactors.add("Within 2 hours")
            } else if (timeDiffHours <= 24.0) {
                score += 10
                currentFactors.add("Within 24 hours")
            }

            // 4. Normalized Merchant match (20 points)
            val normIncoming = normalizeMerchant(incoming.counterparty)
            val normExisting = normalizeMerchant(existing.counterparty)

            if (normIncoming.isNotEmpty() && normExisting.isNotEmpty()) {
                if (normIncoming == normExisting) {
                    score += 20
                    currentFactors.add("Identical merchant name")
                } else if (normIncoming.contains(normExisting) || normExisting.contains(normIncoming)) {
                    score += 15
                    currentFactors.add("Partial merchant name match")
                } else {
                    val similarity = calculateJaccardSimilarity(normIncoming, normExisting)
                    if (similarity >= 0.6) {
                        score += 10
                        currentFactors.add("Fuzzy merchant similarity")
                    }
                }
            }

            if (score > highestScore) {
                highestScore = score
                bestMatch = existing
                bestFactors = currentFactors
            }
        }

        // Evaluation thresholds:
        // Score >= 80: Auto-merged
        // Score 60 - 79: Queued to duplicate resolution queue
        // Score < 60: Unique new record
        if (highestScore >= 80 && bestMatch != null) {
            val merged = bestMatch.copy(
                receiptImagePath = incoming.receiptImagePath ?: bestMatch.receiptImagePath,
                notes = if (bestMatch.notes.isNullOrBlank()) incoming.notes else bestMatch.notes,
                isVerified = true
            )
            return ReconciliationResult(
                outcome = ReconciliationOutcome.AUTO_MERGED,
                resolvedTransaction = merged,
                matchScore = highestScore,
                matchFactors = bestFactors
            )
        } else if (highestScore >= 60 && bestMatch != null) {
            val candidate = DuplicateEntity(
                id = "dup-${UUID.randomUUID()}",
                existingTransactionId = bestMatch.id,
                importedExternalRef = incoming.externalRef,
                importedAccountId = incoming.accountId,
                importedCategoryId = incoming.categoryId,
                importedAmount = incoming.amount,
                importedCurrency = incoming.currency,
                importedDirection = incoming.direction,
                importedType = incoming.type,
                importedTimestamp = incoming.timestamp,
                importedCounterparty = incoming.counterparty,
                importedSourceMethod = incoming.sourceMethod,
                importedNotes = incoming.notes,
                importedReceiptImagePath = incoming.receiptImagePath,
                importedAccountRail = incoming.accountRail,
                matchScore = highestScore,
                matchFactors = bestFactors.joinToString(";")
            )
            return ReconciliationResult(
                outcome = ReconciliationOutcome.QUEUED_FOR_REVIEW,
                candidateForQueue = candidate,
                matchScore = highestScore,
                matchFactors = bestFactors
            )
        }

        return ReconciliationResult(
            outcome = ReconciliationOutcome.INSERTED_NEW,
            resolvedTransaction = incoming,
            matchScore = highestScore,
            matchFactors = bestFactors
        )
    }

    private fun normalizeMerchant(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun calculateJaccardSimilarity(s1: String, s2: String): Double {
        val set1 = s1.split(" ").toSet()
        val set2 = s2.split(" ").toSet()
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        if (union == 0) return 0.0
        return intersection.toDouble() / union.toDouble()
    }

    private fun isMoMoRail(rail: String): Boolean {
        val r = rail.lowercase()
        return r.contains("momo") || r.contains("mtn") || r.contains("mobile money") ||
                r.contains("telecel") || r.contains("vodafone") || r.contains("cash") ||
                r.contains("airteltigo") || r.contains("at money") || r.contains("wallet")
    }

    private fun isBankRail(rail: String): Boolean {
        val r = rail.lowercase()
        return r.contains("bank") || r.contains("ecobank") || r.contains("stanbic") ||
                r.contains("gcb") || r.contains("gtbank") || r.contains("zenith") ||
                r.contains("absa") || r.contains("fidelity") || r.contains("calbank") ||
                r.contains("access") || r.contains("standard chartered") || r.contains("fnb") ||
                r.contains("cbg")
    }
}
