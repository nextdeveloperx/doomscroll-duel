package com.doomscrollduel.analytics

import android.content.Context
import com.doomscrollduel.domain.analytics.Analytics
import com.doomscrollduel.domain.analytics.AnalyticsEvent
import com.doomscrollduel.domain.analytics.AnalyticsPermission
import com.doomscrollduel.domain.analytics.PermissionTransitions
import com.doomscrollduel.tracking.health.TrackingHealthMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Sends `permission_granted` when a permission turns on. The last seen state is kept on the phone so a permission switched
 * on in Android Settings (which can restart our process) is still noticed; the very first look is only a baseline.
 */
@Singleton
class PermissionAnalytics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monitor: TrackingHealthMonitor,
    private val analytics: Analytics,
) {
    private val prefs get() = context.getSharedPreferences("analytics_permission_state", Context.MODE_PRIVATE)

    fun start(scope: CoroutineScope) {
        scope.launch {
            monitor.observe().collect { health ->
                val current = mapOf(
                    AnalyticsPermission.ACCESSIBILITY to health.accessibilityEnabled,
                    AnalyticsPermission.NOTIFICATIONS to health.notificationsEnabled,
                    AnalyticsPermission.BATTERY to health.batteryUnrestricted,
                )
                val previous = AnalyticsPermission.entries
                    .takeIf { all -> all.all { prefs.contains(it.token) } }
                    ?.associateWith { prefs.getBoolean(it.token, false) }
                PermissionTransitions.granted(previous, current).forEach { analytics.track(AnalyticsEvent.PermissionGranted(it)) }
                prefs.edit().apply { current.forEach { (p, on) -> putBoolean(p.token, on) } }.apply()
            }
        }
    }
}
