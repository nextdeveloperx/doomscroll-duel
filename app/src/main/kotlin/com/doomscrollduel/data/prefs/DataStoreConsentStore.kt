package com.doomscrollduel.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.doomscrollduel.domain.legal.ConsentRecord
import com.doomscrollduel.domain.legal.ConsentStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private val Context.consentDataStore by preferencesDataStore(name = "consent")
private val VERSION_KEY = intPreferencesKey("accessibility_disclosure_version")
private val ACCEPTED_AT_KEY = longPreferencesKey("accessibility_disclosure_accepted_at")

/**
 * The person's answer to the Accessibility disclosure, kept on the phone. Read once at start-up (the service needs it
 * on the very first event), held in memory, written in the background. A missing or unreadable file is "no consent".
 */
@Singleton
class DataStoreConsentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConsentStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(readAtStartUp())

    override val flow: StateFlow<ConsentRecord?> = state

    override fun current(): ConsentRecord? = state.value

    override fun save(record: ConsentRecord) {
        state.value = record
        scope.launch {
            context.consentDataStore.edit {
                it[VERSION_KEY] = record.version
                it[ACCEPTED_AT_KEY] = record.acceptedAtMs
            }
        }
    }

    private fun readAtStartUp(): ConsentRecord? = runCatching {
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(3_000L) {
                val prefs = context.consentDataStore.data.first()
                val version = prefs[VERSION_KEY]
                val at = prefs[ACCEPTED_AT_KEY]
                if (version != null && at != null) ConsentRecord(version, at) else null
            }
        }
    }.getOrNull()
}
