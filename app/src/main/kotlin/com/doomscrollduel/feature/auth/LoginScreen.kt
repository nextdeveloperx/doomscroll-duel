package com.doomscrollduel.feature.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.Brain3D
import com.doomscrollduel.core.designsystem.brain.Brain3DMode
import com.doomscrollduel.core.designsystem.brain.BrainOwner
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon

@Composable
fun LoginRoute(
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    LoginScreen(
        ui = ui,
        onGoogle = { activity?.let(viewModel::signInWithGoogle) },
        onOpenPrivacy = onOpenPrivacy,
        onOpenTerms = onOpenTerms,
        modifier = modifier,
    )
}

@Composable
fun LoginScreen(
    ui: LoginUi,
    onGoogle: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val working = ui == LoginUi.Working
    KitPage(
        modifier = modifier,
        bottomSpace = 200.dp,
        bottom = {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GoogleButton(
                    text = stringResource(if (working) R.string.auth_login_working else R.string.auth_login_google),
                    onClick = onGoogle,
                    enabled = !working,
                )
                NText(stringResource(R.string.auth_login_terms), 12.sp, color = Neon.Muted, align = TextAlign.Center, lineHeight = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LinkText(stringResource(R.string.settings_terms), onOpenTerms)
                    LinkText(stringResource(R.string.settings_privacy_policy), onOpenPrivacy)
                }
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            com.doomscrollduel.core.designsystem.components.DoomScrollWordmark(height = 46.dp)
        }
        Brain3D(
            mode = Brain3DMode.SINGLE,
            state = BrainState.HAPPY,
            owner = BrainOwner.YOU,
            percentUsed = 0,
            contentDescription = stringResource(R.string.ds_brain_you_happy),
            modifier = Modifier.fillMaxWidth().height(260.dp),
            fallback = {
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    BrainView(BrainState.HAPPY, Modifier.width(190.dp))
                }
            },
        )
        NText(
            text = stringResource(R.string.auth_login_title),
            size = 32.sp,
            weight = FontWeight.Black,
            align = TextAlign.Center,
            lineHeight = 38.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        NText(
            text = stringResource(R.string.auth_login_sub),
            size = 15.sp,
            color = Neon.Muted,
            align = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        if (ui is LoginUi.Problem) {
            Spacer(Modifier.height(18.dp))
            KitCard(
                fill = Brush.horizontalGradient(listOf(Kit.Red.copy(alpha = 0.2f), Kit.Red.copy(alpha = 0.2f))),
                edge = Kit.Red.copy(alpha = 0.5f),
                padding = PaddingValues(16.dp),
            ) {
                NText(stringResource(ui.kind.message()), 14.sp, weight = FontWeight.Bold, lineHeight = 19.sp)
            }
        }
    }
}

private fun LoginProblem.message(): Int = when (this) {
    LoginProblem.NOT_CONFIGURED -> R.string.auth_err_not_configured
    LoginProblem.NO_ACCOUNT -> R.string.auth_err_no_account
    LoginProblem.NETWORK -> R.string.auth_err_network
    LoginProblem.FAILED -> R.string.auth_err_failed
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    NText(
        text = text,
        size = 14.sp,
        color = Neon.VioletLight,
        weight = FontWeight.ExtraBold,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(name = "Login", widthDp = 390, heightDp = 844)
@Composable
private fun LoginPreview() = ScreenPreview {
    LoginScreen(ui = LoginUi.Idle, onGoogle = {}, onOpenPrivacy = {}, onOpenTerms = {})
}

@Preview(name = "Login error", widthDp = 390, heightDp = 844)
@Composable
private fun LoginErrorPreview() = ScreenPreview {
    LoginScreen(ui = LoginUi.Problem(LoginProblem.NOT_CONFIGURED), onGoogle = {}, onOpenPrivacy = {}, onOpenTerms = {})
}


/**
 * "Sign in with Google": a white pill with the four-colour G and dark text, as Google's branding asks, so it also stands out
 * from the pink buttons on the dark page. Ink is #1F1F1F on white (contrast above 16:1).
 */
@Composable
private fun GoogleButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    val pill = RoundedCornerShape(50)
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(pill)
            .background(Color.White)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        GoogleLogo(Modifier.size(24.dp))
        NText(text, 16.sp, color = Color(0xFF1F1F1F), weight = FontWeight.ExtraBold, maxLines = 1)
    }
}

private const val GOOGLE_RED = "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
private const val GOOGLE_BLUE = "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
private const val GOOGLE_YELLOW = "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
private const val GOOGLE_GREEN = "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"

/** The Google "G", drawn from its official 48 x 48 outline so it needs no image file. Decorative; the button text says it. */
@Composable
private fun GoogleLogo(modifier: Modifier = Modifier) {
    val parts = remember {
        listOf(
            GOOGLE_RED to Color(0xFFEA4335),
            GOOGLE_BLUE to Color(0xFF4285F4),
            GOOGLE_YELLOW to Color(0xFFFBBC05),
            GOOGLE_GREEN to Color(0xFF34A853),
        ).map { (d, color) -> PathParser().parsePathString(d).toPath() to color }
    }
    Canvas(modifier) {
        val k = size.minDimension / 48f
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            parts.forEach { (path, color) -> drawPath(path, color) }
        }
    }
}
