package com.doomscrollduel.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.doomscrollduel.domain.analytics.UsageDataChoice
import com.doomscrollduel.domain.analytics.UsageDataChoiceStore
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

private val Context.usageDataStore by preferencesDataStore(name = "usage_data")
private val CHOICE_KEY = stringPreferencesKey("choice")

/** "Anonymous data": undecided until the person answers. Read once at start-up so nothing is sent before it is known. */
@Singleton
class DataStoreUsageDataChoice @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsageDataChoiceStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(readAtStartUp())

    override val choice: StateFlow<UsageDataChoice> = state

    override fun set(choice: UsageDataChoice) {
        state.value = choice
        scope.launch { context.usageDataStore.edit { it[CHOICE_KEY] = choice.name } }
    }

    private fun readAtStartUp(): UsageDataChoice = runCatching {
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(3_000L) { context.usageDataStore.data.first()[CHOICE_KEY] }
        }
    }.getOrNull()?.let { name -> UsageDataChoice.entries.firstOrNull { it.name == name } } ?: UsageDataChoice.UNDECIDED
}
