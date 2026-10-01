package com.doomscrollduel.analytics

import android.content.Context
import android.os.Bundle
import com.doomscrollduel.domain.analytics.AnalyticsSink
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Sends already-checked events to Firebase Analytics. Parameters are plain strings from fixed lists. */
@Singleton
class FirebaseAnalyticsSink @Inject constructor(
    @ApplicationContext private val context: Context,
) : AnalyticsSink {
    private val firebase get() = FirebaseAnalytics.getInstance(context)

    override fun log(name: String, params: Map<String, String>) {
        val bundle = Bundle()
        params.forEach { (k, v) -> bundle.putString(k, v) }
        firebase.logEvent(name, bundle)
    }

    override fun setUserProperty(name: String, value: String) {
        firebase.setUserProperty(name, value)
    }
}
