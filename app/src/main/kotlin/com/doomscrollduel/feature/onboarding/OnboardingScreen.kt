package com.doomscrollduel.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.R
import com.doomscrollduel.core.common.openAccessibilitySettings
import com.doomscrollduel.core.common.openAppNotificationSettings
import com.doomscrollduel.core.designsystem.components.DoomColors
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.core.designsystem.theme.LilitaOne
import com.doomscrollduel.core.designsystem.theme.Nunito
import com.doomscrollduel.domain.onboarding.AgeRule
import com.doomscrollduel.domain.onboarding.Typing
import com.doomscrollduel.domain.social.ProfileState
import com.doomscrollduel.feature.auth.UsernameUi
import com.doomscrollduel.feature.auth.UsernameViewModel
import com.doomscrollduel.feature.auth.status
import com.doomscrollduel.feature.legal.AccessibilityEntryViewModel
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.NeonIcons
import com.doomscrollduel.tracking.health.TrackingHealth

private const val STEPS = 4

/**
 * The four first-run pages: username, age, permissions, welcome. Every page types its own words out letter by letter, and the
 * phone gives a small vibration tick for each letter. Tapping the words skips the typing; "Remove animations" shows them
 * at once and stays silent.
 */
@Composable
fun OnboardingRoute(
    onOpenDisclosure: () -> Unit,
    onOpenBatteryGuide: () -> Unit,
    onFinished: (pendingInvite: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
    usernameViewModel: UsernameViewModel = hiltViewModel(),
    entry: AccessibilityEntryViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val usernameUi by usernameViewModel.ui.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var age by rememberSaveable { mutableIntStateOf(viewModel.savedAge) }
    var savingName by rememberSaveable { mutableStateOf(false) }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Coming back from a system screen (Accessibility, battery, notifications): look again at what is on.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // The username was claimed: the profile appears and the page moves on by itself.
    LaunchedEffect(savingName, profile) { if (savingName && profile is ProfileState.Ready && step == 0) step = 1 }
    BackHandler(enabled = step > 0) { step -= 1 }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshPermissions() }

    val accessibilityOn = permissions.accessibilityEnabled && permissions.consentGiven
    val skipped = listOf(accessibilityOn, permissions.notificationsEnabled, permissions.batteryUnrestricted).count { !it }

    OnboardingScreen(
        step = step,
        profile = profile,
        usernameUi = usernameUi,
        age = age,
        permissions = permissions,
        accessibilityOn = accessibilityOn,
        onBack = { if (step > 0) step -= 1 },
        onUsernameText = usernameViewModel::onTextChange,
        onSaveUsername = {
            savingName = true
            usernameViewModel.save()
        },
        onAgeChange = { age = AgeRule.clamp(it) },
        onAgeNext = {
            viewModel.saveAge(age)
            step = 2
        },
        onAllowAccessibility = {
            if (entry.mayOpenSettings()) context.openAccessibilitySettings() else onOpenDisclosure()
        },
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= 33 && !askedNotifications) {
                askedNotifications = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openAppNotificationSettings()
            }
        },
        onAllowBattery = onOpenBatteryGuide,
        onNext = { step += 1 },
        onFinish = {
            viewModel.finish(skipped)
            onFinished(viewModel.pendingInvite)
        },
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    step: Int,
    profile: ProfileState,
    usernameUi: UsernameUi,
    age: Int,
    permissions: TrackingHealth,
    accessibilityOn: Boolean,
    onBack: () -> Unit,
    onUsernameText: (String) -> Unit,
    onSaveUsername: () -> Unit,
    onAgeChange: (Int) -> Unit,
    onAgeNext: () -> Unit,
    onAllowAccessibility: () -> Unit,
    onAllowNotifications: () -> Unit,
    onAllowBattery: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calm = DuelTheme.motion.reduced
    val buzzer = rememberBuzzer()
    val alreadyNamed = profile as? ProfileState.Ready
    val noServer = profile == ProfileState.Unavailable
    // The username is the name friends will know them by, so the welcome uses it.
    val firstName = alreadyNamed?.profile?.username ?: stringResource(R.string.onb_welcome_friend)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(DoomColors.InkLift, DoomColors.Ink)))
                drawRect(
                    Brush.radialGradient(
                        listOf(DoomColors.Crimson.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.12f), radius = size.width * 1.0f,
                    ),
                )
            },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(14.dp))
            ProgressSegments(step = step)
            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        DuelIcon(NeonIcons.Back, tint = DoomColors.Bone, contentDescription = stringResource(R.string.onb_back), modifier = Modifier.size(24.dp))
                    }
                }
            }

            // One typed block per page; `key` restarts the typing on every page.
            androidx.compose.runtime.key(step) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (step) {
                        0 -> PageUsername(usernameUi, alreadyNamed?.profile?.username, noServer, calm, buzzer, onUsernameText)
                        1 -> PageAge(age, calm, buzzer, onAgeChange)
                        2 -> PagePermissions(permissions, accessibilityOn, calm, buzzer, onAllowAccessibility, onAllowNotifications, onAllowBattery)
                        else -> PageWelcome(firstName, calm, buzzer)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            val (label, enabled, action) = when (step) {
                0 -> if (alreadyNamed != null || noServer) {
                    Triple(stringResource(R.string.onb_next), true, onNext)
                } else {
                    Triple(
                        stringResource(if (usernameUi.saving) R.string.username_saving else R.string.onb_next),
                        usernameUi.canSave,
                        onSaveUsername,
                    )
                }
                1 -> Triple(stringResource(R.string.onb_next), AgeRule.allowed(age), onAgeNext)
                2 -> Triple(stringResource(R.string.onb_next), true, onNext)
                else -> Triple(stringResource(R.string.onb_start), true, onFinish)
            }
            DoomButton(text = label, onClick = action, enabled = enabled, modifier = Modifier.padding(bottom = 18.dp))
        }
    }
}

// ---- pages -------------------------------------------------------------------------------------------------------------

@Composable
private fun PageUsername(
    ui: UsernameUi,
    existing: String?,
    noServer: Boolean,
    calm: Boolean,
    buzzer: Buzzer,
    onText: (String) -> Unit,
) {
    val title = if (existing != null) stringResource(R.string.onb_user_have_title, existing) else stringResource(R.string.username_title)
    val body = when {
        existing != null -> stringResource(R.string.onb_user_have_body)
        noServer -> stringResource(R.string.onb_user_offline_body)
        else -> stringResource(R.string.username_sub)
    }
    var typed by remember { mutableStateOf(calm) }
    PageHead(title = title, body = body, calm = calm, buzzer = buzzer, art = 120, onTyped = { typed = true })
    if (existing == null && !noServer) {
        Spacer(Modifier.height(24.dp))
        Reveal(visible = typed || calm) {
            Column {
                val (message, tone) = status(ui)
                DoomField(
                    value = ui.text,
                    onValueChange = onText,
                    hint = stringResource(R.string.username_hint),
                    enabled = !ui.saving,
                    prefix = "@",
                    error = tone == com.doomscrollduel.feature.common.Kit.Red,
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                )
                Spacer(Modifier.height(12.dp))
                if (message != null) {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        NText(message, 14.sp, color = tone, weight = FontWeight.Bold, lineHeight = 19.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageAge(age: Int, calm: Boolean, buzzer: Buzzer, onChange: (Int) -> Unit) {
    var typed by remember { mutableStateOf(calm) }
    PageHead(
        title = stringResource(R.string.onb_age_title),
        body = stringResource(R.string.onb_age_body),
        calm = calm, buzzer = buzzer, art = 120, onTyped = { typed = true },
    )
    Spacer(Modifier.height(28.dp))
    Reveal(visible = typed || calm) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                RoundStep(
                    icon = NeonIcons.Back,
                    description = stringResource(R.string.onb_age_less),
                    enabled = age > AgeRule.LOWEST_SHOWN,
                    onClick = { onChange(age - 1) },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val summary = stringResource(R.string.onb_age_value, age)
                    androidx.compose.foundation.text.BasicText(
                        text = age.toString(),
                        style = TextStyle(fontFamily = LilitaOne, fontSize = 72.sp, color = DoomColors.Bone, textAlign = TextAlign.Center),
                        modifier = Modifier.semantics { contentDescription = summary },
                    )
                    NText(stringResource(R.string.onb_age_years), 15.sp, color = DoomColors.Ash)
                }
                RoundStep(
                    icon = NeonIcons.Plus,
                    description = stringResource(R.string.onb_age_more),
                    enabled = age < AgeRule.MAX_AGE,
                    onClick = { onChange(age + 1) },
                )
            }
            if (!AgeRule.allowed(age)) {
                Spacer(Modifier.height(18.dp))
                NText(
                    stringResource(R.string.onb_age_too_young, AgeRule.MIN_AGE),
                    14.sp, color = Color(0xFFFFB35C), weight = FontWeight.Bold, lineHeight = 19.sp, align = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun PagePermissions(
    health: TrackingHealth,
    accessibilityOn: Boolean,
    calm: Boolean,
    buzzer: Buzzer,
    onAccessibility: () -> Unit,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
) {
    var typed by remember { mutableStateOf(calm) }
    PageHead(
        title = stringResource(R.string.onb_perm_title),
        body = stringResource(R.string.onb_perm_body),
        calm = calm, buzzer = buzzer, art = 96, onTyped = { typed = true },
    )
    Spacer(Modifier.height(20.dp))
    Reveal(visible = typed || calm) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PermissionRow(NeonIcons.Reels, stringResource(R.string.onb_perm_a11y), stringResource(R.string.onb_perm_a11y_sub), accessibilityOn, onAccessibility)
            PermissionRow(NeonIcons.Bell, stringResource(R.string.onb_perm_notif), stringResource(R.string.onb_perm_notif_sub), health.notificationsEnabled, onNotifications)
            PermissionRow(NeonIcons.Bolt, stringResource(R.string.onb_perm_battery), stringResource(R.string.onb_perm_battery_sub), health.batteryUnrestricted, onBattery)
        }
    }
}

@Composable
private fun PageWelcome(name: String, calm: Boolean, buzzer: Buzzer) {
    PageHead(
        title = stringResource(R.string.onb_welcome_title, name),
        body = stringResource(R.string.onb_welcome_body),
        calm = calm, buzzer = buzzer, art = 190, bob = true, onTyped = {},
    )
}

// ---- pieces ------------------------------------------------------------------------------------------------------------

/** The little artwork, then the page's heading and body, typed one after the other. */
@Composable
private fun PageHead(
    title: String,
    body: String,
    calm: Boolean,
    buzzer: Buzzer,
    art: Int,
    onTyped: () -> Unit,
    bob: Boolean = false,
) {
    var headingDone by remember { mutableStateOf(calm) }
    var skipped by remember { mutableStateOf(false) }
    val instant = calm || skipped
    val bobY = if (bob && !calm) {
        val t = rememberInfiniteTransition(label = "bob")
        val v by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "bobY")
        v
    } else 0f
    Image(
        painter = painterResource(R.drawable.splash_art),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .height(art.dp)
            .graphicsLayer { translationY = bobY.dp.toPx() },
    )
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skipped = true },
    ) {
        TypewriterText(
            text = title,
            style = TextStyle(fontFamily = LilitaOne, fontSize = 32.sp, lineHeight = 38.sp, color = DoomColors.Bone),
            accent = DoomColors.Crimson,
            msPerChar = Typing.HEADING_MS,
            active = true,
            instant = instant,
            buzzer = buzzer,
            onDone = { headingDone = true },
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        TypewriterText(
            text = body,
            style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, color = DoomColors.Ash),
            accent = DoomColors.Crimson,
            msPerChar = Typing.BODY_MS,
            active = headingDone,
            instant = instant,
            buzzer = buzzer,
            onDone = onTyped,
        )
    }
}

/** Page content (a field, a stepper, the permission list) fades in once the words above it are typed. */
@Composable
private fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 6 },
    ) { content() }
}

@Composable
private fun ProgressSegments(step: Int) {
    val describe = stringResource(R.string.onb_step_label, step + 1, STEPS)
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = describe },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(STEPS) { i ->
            val color by animateColorAsState(if (i <= step) DoomColors.Crimson else Color.White.copy(alpha = 0.14f), tween(300), label = "seg$i")
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun DoomButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(DoomColors.Ember, DoomColors.Crimson)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        NText(text, 17.sp, color = Color.White, weight = FontWeight.Black, align = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun DoomField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    enabled: Boolean,
    prefix: String,
    error: Boolean,
    keyboard: KeyboardOptions,
) {
    var focused by remember { mutableStateOf(false) }
    val border = when {
        error -> Color(0xFFFF6B8A)
        focused -> DoomColors.Crimson
        else -> Color.White.copy(alpha = 0.16f)
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.5.dp, border, shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NText(prefix, 20.sp, color = DoomColors.Crimson, weight = FontWeight.Black)
        Spacer(Modifier.size(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) NText(hint, 18.sp, color = DoomColors.Ash.copy(alpha = 0.6f), weight = FontWeight.SemiBold, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = keyboard,
                textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = DoomColors.Bone),
                cursorBrush = SolidColor(DoomColors.Crimson),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@Composable
private fun RoundStep(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.5.dp, DoomColors.Crimson.copy(alpha = 0.8f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The "less" button reuses the back arrow turned into a minus bar, the "more" button the plus.
        if (icon == NeonIcons.Plus) {
            DuelIcon(icon, tint = DoomColors.Bone, contentDescription = description, modifier = Modifier.size(26.dp))
        } else {
            Box(
                Modifier
                    .size(width = 20.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(DoomColors.Bone)
                    .clearAndSetSemantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    on: Boolean,
    onAllow: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val onText = stringResource(R.string.onb_perm_on)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (on) DoomColors.Crimson.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(DoomColors.Crimson.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            DuelIcon(icon, tint = DoomColors.Crimson, contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            NText(title, 16.sp, color = DoomColors.Bone, weight = FontWeight.ExtraBold)
            NText(sub, 13.sp, color = DoomColors.Ash, lineHeight = 18.sp)
        }
        if (on) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF5EEAA0).copy(alpha = 0.18f)).semantics { contentDescription = onText },
                contentAlignment = Alignment.Center,
            ) {
                DuelIcon(NeonIcons.Check, tint = Color(0xFF5EEAA0), contentDescription = null, modifier = Modifier.size(20.dp))
            }
        } else {
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(DoomColors.Crimson)
                    .clickable(role = Role.Button, onClick = onAllow)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                NText(stringResource(R.string.onb_perm_allow), 13.sp, color = Color.White, weight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}
