package com.doomscrollduel

import android.app.Application
import com.doomscrollduel.analytics.PermissionAnalytics
import com.doomscrollduel.analytics.UsageDataApplier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class DuelApp : Application() {
    @Inject lateinit var usageData: UsageDataApplier
    @Inject lateinit var permissionAnalytics: PermissionAnalytics
    @Inject lateinit var pushTokens: com.doomscrollduel.feature.friends.PushTokenRegistrar
    @Inject lateinit var inboxNotifier: com.doomscrollduel.data.social.InboxNotifier
    @Inject lateinit var duelTracker: com.doomscrollduel.data.social.DuelTracker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Analytics and Crashlytics are off in the manifest; this switches them to match the person's answer.
        usageData.start(scope)
        permissionAnalytics.start(scope)
        pushTokens.start(scope)
        inboxNotifier.start(scope)
        duelTracker.start(scope)
        android.os.Handler(android.os.Looper.getMainLooper()).post { com.doomscrollduel.core.designsystem.brain.prewarmBrain3D(this) }
    }
}
