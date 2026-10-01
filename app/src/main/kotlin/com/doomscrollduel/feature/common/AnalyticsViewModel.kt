package com.doomscrollduel.feature.common

import androidx.lifecycle.ViewModel
import com.doomscrollduel.domain.analytics.Analytics
import com.doomscrollduel.domain.analytics.AnalyticsEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Lets a screen that has no ViewModel of its own send one analytics event. Events are checked and consent-gated in [Analytics]. */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(private val analytics: Analytics) : ViewModel() {
    fun track(event: AnalyticsEvent) = analytics.track(event)
}
