package com.doomscrollduel.core.designsystem.brain

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/** What the 3D page shows: one brain on a spotlight, or the two fighters facing off. */
enum class Brain3DMode { SINGLE, FACE_OFF }

/**
 * A real 3D brain (Three.js, bundled in `assets/brain3d`) shown inside a WebView. The brain is built in code with
 * folds, a cerebellum, a brainstem and a glossy clear-coat, so it needs no model file and works offline.
 *
 * - The page is transparent where nothing is drawn, so it sits on any background.
 * - The WebView never takes touches (a transparent layer on top swallows them), so screens that scroll still scroll.
 * - It pauses with the screen's lifecycle, and with "Remove animations" on it draws one still frame.
 * - If the page cannot load (no WebView, no WebGL) [fallback] is drawn instead, so a screen is never empty.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Brain3D(
    mode: Brain3DMode,
    state: BrainState,
    owner: BrainOwner,
    /** Whole percent of today's reel limit used. 0 is a fresh, cheerful brain; the more reels, the more bored it looks; 100 and over is a zombie. */
    percentUsed: Int = 0,
    contentDescription: String,
    modifier: Modifier = Modifier,
    /** Face-off only: a dark red and blue arena instead of the yellow sun. */
    arena: Boolean = false,
    fallback: @Composable () -> Unit = {},
) {
    val colors = DuelTheme.colors
    val calm = DuelTheme.motion.reduced
    val you = colors.pink
    val opponent = colors.cyan
    // Realistic skin tones behind the cartoon palette: softer than the flat game colours.
    val body = when (state) {
        BrainState.HAPPY -> if (owner == BrainOwner.YOU) PinkFlesh else BlueFlesh
        BrainState.FRIED -> FriedFlesh
        BrainState.ZOMBIE -> ZombieFlesh
    }
    val url = remember(mode, state, owner, calm, percentUsed, arena) {
        val m = if (mode == Brain3DMode.FACE_OFF) "faceoff" else "single"
        "file:///android_asset/brain3d/brain.html?mode=$m&p=${(percentUsed.coerceIn(0, 100) / 5) * 5}" +
            "&color=${hex(body)}&color2=${hex(BlueFlesh)}&calm=${if (calm) 1 else 0}&arena=${if (arena) 1 else 0}"
    }
    // The first numbers on Home are a placeholder (0 reels) that the real ones replace a moment later. Waiting for the address to hold
    // still means the page is loaded once with the right brain, not twice with a blank gap in between.
    var settledUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        if (settledUrl != null) delay(150) else delay(350)
        settledUrl = url
    }
    val pageUrl = settledUrl
    var failed by remember(pageUrl) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val webView = remember(pageUrl) {
        if (pageUrl == null) return@remember null
        runCatching {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                isClickable = false
                isFocusable = false
                importantForAccessibility = WebView.IMPORTANT_FOR_ACCESSIBILITY_NO
                settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = true
                    allowContentAccess = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mediaPlaybackRequiresUserGesture = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        // The page has run; give its first frames a moment to draw before the splash may lift.
                        view.postDelayed({ Brain3DReady.markReady() }, 450)
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) failed = true
                    }
                }
                loadUrl(pageUrl)
            }
        }.getOrNull()
    }
    if (webView == null && pageUrl != null) failed = true

    DisposableEffect(lifecycle, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
        }
    }

    Box(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        },
    ) {
        if (failed) {
            fallback()
        } else if (webView == null) {
            // Waiting for the page address to settle: nothing is drawn yet.
        } else {
            // keyed on the view: a new WebView must replace the old one, or the destroyed one stays on screen blank.
            key(webView) { AndroidView(factory = { webView }, modifier = Modifier.matchParentSize()) }
            // Swallows touches so the WebView never steals a scroll.
            Box(Modifier.matchParentSize().pointerInput(Unit) { })
        }
    }
}

/** Starts the browser engine and reads the 3D page once, at app start, so the Home brain is not the first thing to wake it. */
fun prewarmBrain3D(context: android.content.Context) {
    runCatching {
        val warm = WebView(context.applicationContext)
        warm.settings.javaScriptEnabled = true
        warm.settings.allowFileAccess = true
        warm.settings.allowContentAccess = false
        warm.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.postDelayed({ view.destroy() }, 500)
            }
        }
        warm.loadUrl("file:///android_asset/brain3d/brain.html?mode=single&p=0&freeze=1")
    }
}

private val PinkFlesh = Color(0xFFF58BB0)
private val BlueFlesh = Color(0xFF5BC8FF)
private val FriedFlesh = Color(0xFFFFA45C)
private val ZombieFlesh = Color(0xFF9BDB7A)

private fun hex(c: Color): String {
    val argb = android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    return String.format("%06x", argb and 0xFFFFFF)
}
