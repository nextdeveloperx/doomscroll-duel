package com.doomscrollduel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.doomscrollduel.feature.splash.SplashGate
import com.doomscrollduel.feature.splash.SplashOverlay
import com.doomscrollduel.billing.BillingManager
import com.doomscrollduel.billing.EntitlementService
import com.doomscrollduel.blocking.BlockingScheduler
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.navigation.DuelNavGraph
import com.doomscrollduel.tracking.service.TrackingKeepAlive
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var scheduler: BlockingScheduler
    @Inject lateinit var billing: BillingManager
    @Inject lateinit var entitlements: EntitlementService

    /** An invite link or a tapped notification that arrives while the app is already open. */
    private val newIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DuelTheme {
                // The app is composed from the first moment, so the Home brain loads while the splash is up.
                var splashDone by remember { mutableStateOf(SplashGate.shown) }
                Box(Modifier.fillMaxSize()) {
                    DuelNavGraph(newIntents = newIntents)
                    if (!splashDone) SplashOverlay(onFinished = { splashDone = true })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        newIntents.tryEmit(intent)
    }

    override fun onStart() {
        super.onStart()
        // The app is on screen, so Android always allows starting the foreground service here.
        TrackingKeepAlive.start(this)
        // Re-arm the wake-up for the lock and for bedtime or focus windows.
        scheduler.schedule()
        // Pick up renewals, a restored purchase or a new phone, and re-check the clock against the stored entitlement.
        billing.syncIfStale()
        entitlements.recheck()
    }
}
