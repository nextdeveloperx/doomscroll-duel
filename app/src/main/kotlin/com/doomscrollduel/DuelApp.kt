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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Analytics and Crashlytics are off in the manifest; this switches them to match the person's answer.
        usageData.start(scope)
        permissionAnalytics.start(scope)
    }
}
