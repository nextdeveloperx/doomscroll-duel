package com.doomscrollduel.data.coins

import android.content.Context
import com.doomscrollduel.domain.coins.MilestoneProgressStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** See [MilestoneProgressStore]. */
@Singleton
class SharedPrefsMilestoneProgressStore @Inject constructor(
    @ApplicationContext context: Context,
) : MilestoneProgressStore {
    private val prefs = context.getSharedPreferences("reel_milestones", Context.MODE_PRIVATE)

    override fun lastCredited(uid: String): Int = prefs.getInt(KEY_PREFIX + uid, 0)

    override fun setLastCredited(uid: String, milestone: Int) {
        prefs.edit().putInt(KEY_PREFIX + uid, milestone).apply()
    }

    private companion object {
        const val KEY_PREFIX = "last_milestone:"
    }
}
