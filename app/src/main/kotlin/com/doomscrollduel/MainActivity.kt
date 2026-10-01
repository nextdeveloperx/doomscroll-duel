package com.doomscrollduel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.doomscrollduel.blocking.BlockingScheduler
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.navigation.DuelNavGraph
import com.doomscrollduel.tracking.service.TrackingKeepAlive
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var scheduler: BlockingScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DuelTheme { DuelNavGraph() }
        }
    }

    override fun onStart() {
        super.onStart()
        // The app is on screen, so Android always allows starting the foreground service here.
        TrackingKeepAlive.start(this)
        // Re-arm the wake-up for the lock and for bedtime or focus windows.
        scheduler.schedule()
    }
}
