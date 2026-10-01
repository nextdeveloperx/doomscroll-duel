package com.doomscrollduel.tracking.health

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import com.doomscrollduel.tracking.service.ReelAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Watches the two things that stop counting: the accessibility service being switched off, and the
 * battery manager restricting the app. The accessibility list is observed live (so the banner appears
 * the moment the user turns it off); battery status has no broadcast, so call [refresh] when the app
 * comes back to the foreground.
 */
@Singleton
class TrackingHealthMonitor @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    fun observe(): Flow<TrackingHealth> =
        merge(accessibilityChanges(), refreshes)
            .onStart { emit(Unit) }
            .map { current() }
            .distinctUntilChanged()

    fun current(): TrackingHealth = TrackingHealth(
        accessibilityEnabled = isAccessibilityEnabled(),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
        notificationsEnabled = context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled(),
    )

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return AccessibilityServiceStatus.isEnabled(
            enabledServices = enabled,
            packageName = context.packageName,
            serviceClassName = ReelAccessibilityService::class.java.name,
        )
    }

    private fun accessibilityChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer,
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
}
