package com.doomscrollduel.billing

import com.doomscrollduel.domain.billing.Entitlement
import com.doomscrollduel.domain.billing.Entitlements
import com.doomscrollduel.domain.billing.ProView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** The server's entitlement for the signed-in person. Emits null when there is none (signed out, no document yet). */
interface EntitlementSource {
    fun observe(): Flow<Entitlement?>
}

/** The last entitlement the server sent, kept on the phone so Pro survives being offline. */
interface EntitlementCache {
    fun load(): Entitlement
    fun save(entitlement: Entitlement)
}

/**
 * The one place the app asks "is this person Pro?". It reads what the SERVER wrote (never a purchase on the phone),
 * keeps a copy for offline use, and re-evaluates by itself at the moment access ends, so a lapsed Pro does not stay
 * Pro until the next app start.
 */
class EntitlementService(
    private val source: EntitlementSource,
    private val cache: EntitlementCache,
    private val nowMs: () -> Long,
    scope: CoroutineScope,
) {
    private val latest = MutableStateFlow(cache.load())
    private val recheck = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val entitlement: StateFlow<Entitlement> = latest

    @OptIn(ExperimentalCoroutinesApi::class)
    val view: StateFlow<ProView> = combine(latest, recheck.onStart { emit(Unit) }) { e, _ -> e }
        .flatMapLatest { e ->
            flow {
                while (true) {
                    emit(Entitlements.view(e, nowMs()))
                    val wait = Entitlements.nextChangeInMs(e, nowMs()) ?: break
                    delay(wait + 1)
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, Entitlements.view(cache.load(), nowMs()))

    val isPro: Boolean get() = view.value.isPro

    init {
        source.observe()
            .onEach { e ->
                // Signed out or no document: the server has no Pro for this account.
                val next = e ?: Entitlement.None
                latest.value = next
                cache.save(next)
            }
            .launchIn(scope)
    }

    /** Call when the app comes to the front or the clock changed: re-reads the time against the stored entitlement. */
    fun recheck() {
        recheck.tryEmit(Unit)
    }
}
