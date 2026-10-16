package com.doomscrollduel.feature.broadcast

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.doomscrollduel.R
import com.doomscrollduel.blocking.OverlayLifecycleOwner
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitAvatar

/**
 * The small floating profile circle shown over everything while a voice room is running and its page is not on screen.
 * It shows the person who is talking (or me when nobody is) and pulses the same two green rings as the circles in the room.
 * Tap opens the room; drag moves it. It needs "Display over other apps" and simply stays away without it.
 */
class BroadcastBubble(
    private val context: Context,
    private val onOpen: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null

    private var name by mutableStateOf("")
    private var speaking by mutableStateOf(false)

    /** Who the circle shows and whether the rings pulse. Cheap; called on every room change. */
    fun update(name: String, speaking: Boolean) {
        this.name = name
        this.speaking = speaking
    }

    val isShown: Boolean get() = view != null

    fun show() {
        if (view != null) return
        val density = context.resources.displayMetrics.density
        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (context.resources.displayMetrics.widthPixels - 100 * density).toInt()
            y = (160 * density).toInt()
        }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                DuelTheme {
                    BubbleContent(
                        name = name,
                        speaking = speaking,
                        onOpen = onOpen,
                        onDrag = { dx, dy ->
                            layout.x += dx.toInt()
                            layout.y += dy.toInt()
                            view?.let { runCatching { windowManager.updateViewLayout(it, layout) } }
                        },
                    )
                }
            }
        }
        runCatching { windowManager.addView(composeView, layout) }
            .onSuccess {
                view = composeView
                owner = lifecycleOwner
                params = layout
            }
            .onFailure { lifecycleOwner.stop() }
    }

    fun hide() {
        val v = view ?: return
        runCatching { windowManager.removeView(v) }
        owner?.stop()
        view = null
        owner = null
        params = null
    }
}

@Composable
private fun BubbleContent(name: String, speaking: Boolean, onOpen: () -> Unit, onDrag: (Float, Float) -> Unit) {
    val reduced = DuelTheme.motion.reduced
    val transition = rememberInfiniteTransition(label = "bubble")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "wave",
    )
    val label = stringResource(R.string.bc_bubble_cd)
    // 56dp circle + 3dp ring inside an 84dp box: the pulse starts at the circle's size and grows outwards, as in the room.
    Box(
        Modifier
            .size(84.dp)
            .semantics { contentDescription = label }
            .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } },
        contentAlignment = Alignment.Center,
    ) {
        if (speaking) {
            if (reduced) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(Kit.Green.copy(alpha = 0.35f)))
            } else {
                listOf(wave, (wave + 0.5f) % 1f).forEach { t ->
                    Box(
                        Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                val scale = 1f + 0.45f * t
                                scaleX = scale
                                scaleY = scale
                                alpha = 0.6f * (1f - t)
                            }
                            .clip(CircleShape)
                            .background(Kit.Green),
                    )
                }
            }
        }
        Box(
            Modifier
                .border(if (speaking) 4.dp else 3.dp, if (speaking) Kit.Green else Kit.Pink, CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpen),
        ) {
            KitAvatar(name.ifBlank { "?" }, size = 44.dp, tone = Kit.Blue)
        }
    }
}
