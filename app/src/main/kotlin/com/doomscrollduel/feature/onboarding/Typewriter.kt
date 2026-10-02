package com.doomscrollduel.feature.onboarding

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.doomscrollduel.domain.onboarding.Typing
import kotlinx.coroutines.delay

/**
 * A short, light tick of the phone's vibration motor. Nothing is read or recorded; if the phone has no vibrator, or the system
 * has vibration switched off, [tick] does nothing.
 */
class Buzzer(private val vibrator: Vibrator?) {
    private val effect: VibrationEffect? = vibrator?.takeIf { it.hasVibrator() }?.let {
        val strength = if (it.hasAmplitudeControl()) TICK_STRENGTH else VibrationEffect.DEFAULT_AMPLITUDE
        VibrationEffect.createOneShot(TICK_MS, strength)
    }

    fun tick() {
        val e = effect ?: return
        runCatching { vibrator?.vibrate(e) }
    }

    fun stop() {
        runCatching { vibrator?.cancel() }
    }

    private companion object {
        const val TICK_MS = 9L
        const val TICK_STRENGTH = 70
    }
}

@Composable
fun rememberBuzzer(): Buzzer {
    val context = LocalContext.current
    val buzzer = remember { Buzzer(context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator) }
    DisposableEffect(buzzer) { onDispose { buzzer.stop() } }
    return buzzer
}

/**
 * Text that types itself out one letter at a time. Each real letter gives the phone a small vibration tick, so the person
 * feels the typing. The newest letter is drawn in [accent]. The full text is laid out from the start (letters not typed yet
 * are transparent), so lines never jump while it types.
 *
 * @param active false keeps the text hidden and waiting (a second block that starts after the first one is done).
 * @param instant true shows everything at once and stays silent: "Remove animations" is on, or the person tapped to skip.
 * @param onDone called once, when the last letter is shown (also when [instant] cuts the typing short).
 */
@Composable
fun TypewriterText(
    text: String,
    style: TextStyle,
    accent: Color,
    msPerChar: Long,
    active: Boolean,
    instant: Boolean,
    buzzer: Buzzer,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by remember(text) { mutableIntStateOf(0) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(text, active, instant) {
        if (instant) {
            typed = text.length
            done()
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        while (typed < text.length) {
            delay(msPerChar)
            typed += 1
            if (Typing.shouldBuzz(text[typed - 1])) buzzer.tick()
        }
        done()
    }
    val shown = typed.coerceIn(0, text.length)
    val annotated = buildAnnotatedString {
        if (shown > 1) append(text.substring(0, shown - 1))
        if (shown > 0) withStyle(SpanStyle(color = if (shown == text.length) style.color else accent)) { append(text[shown - 1]) }
        if (shown < text.length) withStyle(SpanStyle(color = Color.Transparent)) { append(text.substring(shown)) }
    }
    // The screen reader gets the whole sentence at once, not a letter at a time.
    BasicText(text = annotated, style = style, modifier = modifier.semantics { contentDescription = text })
}
