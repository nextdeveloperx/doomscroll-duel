package com.doomscrollduel.store

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.HardShadowText
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.FakeData
import com.doomscrollduel.feature.duel.create.NewDuelScreen
import com.doomscrollduel.feature.duel.live.LiveDuelScreen
import com.doomscrollduel.feature.duel.result.ResultScreen
import com.doomscrollduel.feature.home.HomeScreen
import com.doomscrollduel.feature.modes.BattleModesScreen
import com.doomscrollduel.feature.paywall.PaywallActions
import com.doomscrollduel.feature.paywall.PaywallBusy
import com.doomscrollduel.feature.paywall.PaywallScreen
import com.doomscrollduel.feature.paywall.PaywallUiState
import com.doomscrollduel.billing.StoreState
import com.doomscrollduel.domain.billing.PlanOffer
import com.doomscrollduel.domain.billing.ProPlan
import com.doomscrollduel.domain.billing.ProView
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * Makes the Play Store graphics from the REAL screens, so they are in exactly the app's style and always match the app:
 *  - phone screenshots 1080 x 2160 px (2:1, inside Play's limits), in English and Hinglish, with a caption on top;
 *  - the 512 x 512 px app icon and the 1024 x 500 px feature graphic.
 *
 * Run on an emulator or phone (any density: sizes are set in pixels):
 *   ./gradlew :app:connectedDebugAndroidTest --tests "com.doomscrollduel.store.StoreAssetsTest"
 *   adb pull /sdcard/Android/data/<applicationId>/files/store ./store-assets
 * The screens show sample data (FakeData), never anyone's real numbers.
 */
class StoreAssetsTest {
    @get:Rule val rule = createComposeRule()

    private val outDir: File = InstrumentationRegistry.getInstrumentation().targetContext
        .getExternalFilesDir("store")!!.also { it.mkdirs() }

    private var content by mutableStateOf<@Composable () -> Unit>({})

    /** Fixed 3x density so [px] pixels are always the same number of dp, whatever the device. */
    private fun sizeDp(px: Int): Dp = (px / 3f).dp

    private fun capture(path: String, widthPx: Int, heightPx: Int, body: @Composable () -> Unit) {
        content = {
            Box(Modifier.size(sizeDp(widthPx), sizeDp(heightPx))) { body() }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val file = File(outDir, path).also { it.parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class Shot(val file: String, val en: String, val hi: String, val screen: @Composable () -> Unit)

    private val offers = listOf(
        PlanOffer(ProPlan.MONTHLY, "₹99.00", 99_000_000, "INR", "m"),
        PlanOffer(ProPlan.YEARLY, "₹799.00", 799_000_000, "INR", "y"),
    )

    private val shots = listOf(
        Shot("01_home", "Count your reels. Keep your brain happy.", "Reels ginti raho. Brain khush rakho.") {
            HomeScreen(FakeData.home, {}, {}, {})
        },
        Shot("02_modes", "Five ways to battle your friends.", "Dosto se bhidne ke paanch tareeke.") {
            BattleModesScreen(onModeSelected = {})
        },
        Shot("03_new_duel", "Pick a friend. Set a limit. Go.", "Dost chuno. Limit lagao. Shuru.") {
            NewDuelScreen(FakeData.newDuel, {}, {}, {}, {}, {}, {})
        },
        Shot("04_live", "Fewer reels wins. Live.", "Kam reels wala jeetega. Live.") {
            LiveDuelScreen(FakeData.live, {}, {})
        },
        Shot("05_result", "Win coins. Bragging rights included.", "Coins jeeto. Shekhi alag se.") {
            ResultScreen(FakeData.result.copy(me = FakeData.result.me.copy(reels = 31)), {}, {})
        },
        Shot("06_pro", "Go Pro for squads, locks and stats.", "Squad, locks aur stats ke liye Pro lo.") {
            PaywallScreen(
                PaywallUiState(StoreState.Ready(offers), ProView.Free, ProPlan.YEARLY, PaywallBusy.NONE, null, null),
                PaywallActions({}, {}, {}, {}, {}, {}, {}),
            )
        },
    )

    @Test fun phoneScreenshots() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) { DuelTheme { content() } }
        }
        for (lang in listOf("en", "hi")) {
            for (shot in shots) {
                val caption = if (lang == "en") shot.en else shot.hi
                capture("$lang/${shot.file}.png", 1080, 2160) { StoreFrame(caption, shot.screen) }
            }
        }
    }

    @Test fun appIconAndFeatureGraphic() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) { DuelTheme { content() } }
        }
        capture("icon_512.png", 512, 512) {
            Box(Modifier.fillMaxSize().background(DuelTheme.colors.yellow), contentAlignment = Alignment.Center) {
                BrainView(BrainState.HAPPY, Modifier.width(sizeDp(380)))
            }
        }
        capture("feature_graphic_1024x500.png", 1024, 500) {
            Row(
                Modifier.fillMaxSize().background(DuelTheme.colors.yellow).padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BrainView(BrainState.FRIED, Modifier.width(sizeDp(380)))
                Column(Modifier.weight(1f)) {
                    HardShadowText("Doomscroll", fontSize = 40.sp, textAlign = TextAlign.Start)
                    HardShadowText("Duel", fontSize = 40.sp, textAlign = TextAlign.Start)
                    Spacer(Modifier.height(8.dp))
                    DuelText("Fewer reels wins.", style = DuelTheme.typography.heading, color = DuelTheme.colors.onBright)
                }
            }
        }
    }
}

/** A bright background, a big caption, and the real screen inside a chunky rounded frame (the phone). */
@Composable
private fun StoreFrame(caption: String, screen: @Composable () -> Unit) {
    val colors = DuelTheme.colors
    Column(Modifier.fillMaxSize().background(colors.yellow).padding(top = 28.dp, start = 20.dp, end = 20.dp)) {
        DuelText(
            text = caption,
            style = DuelTheme.typography.title.copy(fontSize = 26.sp),
            color = colors.onBright,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        val frame = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(frame)
                .border(4.dp, colors.outline, frame),
        ) { screen() }
    }
}
