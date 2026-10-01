package com.doomscrollduel.domain.challenge

/**
 * Virtual coins only. This ledger moves coins between players; it never creates or destroys them, and
 * nothing here can be turned into money.
 *
 * Every operation has a stable [id]. Applying an id that was already applied does nothing, so a
 * settlement that runs twice (retry, duplicate delivery, two servers) cannot pay twice.
 */
sealed interface CoinOp {
    val id: String

    /** Moves [amount] from available to held (escrow). */
    data class Hold(override val id: String, val player: PlayerId, val amount: Int) : CoinOp

    /** Moves [amount] from held back to available (refund). */
    data class Release(override val id: String, val player: PlayerId, val amount: Int) : CoinOp

    /** Moves [amount] from [from]'s held coins to [to]'s available coins (the loser pays the winner). */
    data class Transfer(override val id: String, val from: PlayerId, val to: PlayerId, val amount: Int) : CoinOp
}

enum class LedgerError { NON_POSITIVE_AMOUNT, INSUFFICIENT_AVAILABLE, INSUFFICIENT_HELD }

sealed interface LedgerResult {
    data class Applied(val ledger: CoinLedger, val skippedDuplicates: Int) : LedgerResult
    data class Failed(val op: CoinOp, val error: LedgerError) : LedgerResult
}

data class CoinLedger(
    val available: Map<PlayerId, Int> = emptyMap(),
    val held: Map<PlayerId, Int> = emptyMap(),
    val appliedOps: Set<String> = emptySet(),
) {
    fun availableOf(player: PlayerId): Int = available[player] ?: 0

    fun heldOf(player: PlayerId): Int = held[player] ?: 0

    /** All coins in the system. Constant across any settlement. */
    val total: Int get() = available.values.sum() + held.values.sum()

    /** Applies all [ops] or none: if one fails the ledger is unchanged and the failure is returned. */
    fun applyAll(ops: List<CoinOp>): LedgerResult {
        var ledger = this
        var skipped = 0
        for (op in ops) {
            if (op.id in ledger.appliedOps) {
                skipped++
                continue
            }
            ledger = ledger.applyOne(op) ?: return LedgerResult.Failed(op, errorFor(ledger, op))
        }
        return LedgerResult.Applied(ledger, skipped)
    }

    private fun applyOne(op: CoinOp): CoinLedger? = when (op) {
        is CoinOp.Hold ->
            if (op.amount <= 0 || availableOf(op.player) < op.amount) null
            else bump(op.player, available = -op.amount, held = op.amount, id = op.id)
        is CoinOp.Release ->
            if (op.amount <= 0 || heldOf(op.player) < op.amount) null
            else bump(op.player, available = op.amount, held = -op.amount, id = op.id)
        is CoinOp.Transfer ->
            if (op.amount <= 0 || heldOf(op.from) < op.amount) null
            else bump(op.from, held = -op.amount, id = op.id).bump(op.to, available = op.amount, id = op.id)
    }

    private fun bump(player: PlayerId, available: Int = 0, held: Int = 0, id: String): CoinLedger = copy(
        available = this.available + (player to availableOf(player) + available),
        held = this.held + (player to heldOf(player) + held),
        appliedOps = appliedOps + id,
    )

    private fun errorFor(ledger: CoinLedger, op: CoinOp): LedgerError = when (op) {
        is CoinOp.Hold ->
            if (op.amount <= 0) LedgerError.NON_POSITIVE_AMOUNT else LedgerError.INSUFFICIENT_AVAILABLE
        is CoinOp.Release ->
            if (op.amount <= 0) LedgerError.NON_POSITIVE_AMOUNT else LedgerError.INSUFFICIENT_HELD
        is CoinOp.Transfer ->
            if (op.amount <= 0) LedgerError.NON_POSITIVE_AMOUNT else LedgerError.INSUFFICIENT_HELD
    }
}
