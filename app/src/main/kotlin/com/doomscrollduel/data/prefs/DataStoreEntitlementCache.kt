package com.doomscrollduel.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.doomscrollduel.billing.EntitlementCache
import com.doomscrollduel.domain.billing.Entitlement
import com.doomscrollduel.domain.billing.EntitlementCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private val Context.entitlementDataStore by preferencesDataStore(name = "entitlement")
private val ENTITLEMENT_KEY = stringPreferencesKey("entitlement_json")

/** The last entitlement the server sent. Read once at start-up (a short wait), written when it changes. */
@Singleton
class DataStoreEntitlementCache @Inject constructor(
    @ApplicationContext private val context: Context,
) : EntitlementCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun load(): Entitlement = EntitlementCodec.decode(
        runBlocking(Dispatchers.IO) { withTimeoutOrNull(3_000L) { context.entitlementDataStore.data.first()[ENTITLEMENT_KEY] } },
    )

    override fun save(entitlement: Entitlement) {
        val text = EntitlementCodec.encode(entitlement)
        scope.launch { context.entitlementDataStore.edit { it[ENTITLEMENT_KEY] = text } }
    }
}
