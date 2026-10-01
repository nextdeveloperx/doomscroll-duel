package com.doomscrollduel.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.doomscrollduel.domain.repository.ReelLimitStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class SharedPrefsReelLimitStore @Inject constructor(
    @ApplicationContext context: Context,
) : ReelLimitStore {
    private val prefs = context.getSharedPreferences("reel_limit", Context.MODE_PRIVATE)

    override val limit: Flow<Int> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override suspend fun setLimit(limit: Int) {
        prefs.edit().putInt(KEY, limit.coerceIn(ReelLimitStore.MIN, ReelLimitStore.MAX)).apply()
    }

    private fun read(): Int = prefs.getInt(KEY, ReelLimitStore.DEFAULT)

    private companion object {
        const val KEY = "daily_limit"
    }
}
