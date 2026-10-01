package com.doomscrollduel.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.doomscrollduel.domain.blocking.BlockingCodec
import com.doomscrollduel.domain.blocking.BlockingSettings
import com.doomscrollduel.domain.blocking.BlockingState
import com.doomscrollduel.domain.blocking.BlockingStateStore
import com.doomscrollduel.domain.blocking.ObservableBlockingSettings
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

private val Context.blockingDataStore by preferencesDataStore(name = "blocking")

private val SETTINGS_KEY = stringPreferencesKey("settings_json")
private val STATE_KEY = stringPreferencesKey("state_json")

/** One blocking read at start-up. The lock must be known before the first event, so a short wait is correct here. */
private fun Context.readBlocking(key: androidx.datastore.preferences.core.Preferences.Key<String>): String? =
    runBlocking(Dispatchers.IO) {
        withTimeoutOrNull(START_UP_TIMEOUT_MS) { blockingDataStore.data.first()[key] }
    }

private const val START_UP_TIMEOUT_MS = 3_000L

/**
 * Settings in DataStore. The value lives in memory ([flow]) so reading is instant and screens can watch it;
 * writes go to disk in the background. A damaged file falls back to the defaults.
 */
@Singleton
class DataStoreBlockingSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : ObservableBlockingSettings {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(BlockingCodec.decodeSettings(context.readBlocking(SETTINGS_KEY)))

    override val flow: StateFlow<BlockingSettings> = state

    override fun current(): BlockingSettings = state.value

    override fun save(settings: BlockingSettings) {
        state.value = settings
        val text = BlockingCodec.encodeSettings(settings)
        scope.launch { context.blockingDataStore.edit { it[SETTINGS_KEY] = text } }
    }
}

/**
 * The lock, a friend's pass and the friend-request history in DataStore. Written only when one of them starts
 * or ends (see `BlockingController`), read once when the process starts.
 */
@Singleton
class DataStoreBlockingState @Inject constructor(
    @ApplicationContext private val context: Context,
) : BlockingStateStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun load(): BlockingState = BlockingCodec.decodeState(context.readBlocking(STATE_KEY))

    override fun save(state: BlockingState) {
        val text = BlockingCodec.encodeState(state)
        scope.launch { context.blockingDataStore.edit { it[STATE_KEY] = text } }
    }
}
