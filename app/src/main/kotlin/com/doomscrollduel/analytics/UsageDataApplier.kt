package com.doomscrollduel.analytics

import android.content.Context
import com.doomscrollduel.domain.analytics.UsageDataChoice
import com.doomscrollduel.domain.analytics.UsageDataChoiceStore
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Turns Firebase Analytics and Crashlytics on or off to match the person's answer. Both are OFF in the manifest, so
 * nothing is collected before this runs and nothing is collected unless the answer is "allowed".
 *
 * Advertising consent flags are always denied: there are no ads and the advertising ID is not collected.
 */
@Singleton
class UsageDataApplier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: UsageDataChoiceStore,
) {
    private var previous: UsageDataChoice? = null

    /** Applies the stored answer now and again every time it changes. */
    fun start(scope: CoroutineScope) {
        scope.launch { store.choice.collect(::apply) }
    }

    fun apply(choice: UsageDataChoice) {
        val on = choice == UsageDataChoice.ALLOWED
        val analytics = FirebaseAnalytics.getInstance(context)
        analytics.setAnalyticsCollectionEnabled(on)
        analytics.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (on) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            ),
        )
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(on)
        // Someone who had allowed it and now says no: stop, and ask Firebase to forget what it holds for this install.
        if (choice == UsageDataChoice.DECLINED && previous == UsageDataChoice.ALLOWED) {
            analytics.resetAnalyticsData()
            crashlytics.deleteUnsentReports()
        }
        previous = choice
    }
}
