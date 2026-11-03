package com.karelisio.bulle.ui.onboarding

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.system.OemSettings
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.rememberReduceMotion
import com.karelisio.bulle.ui.settings.IslandPreview
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Une page de la prise en main. */
private sealed interface OnboardingPage {
    data class Intro(val index: Int) : OnboardingPage
    data class Permission(val key: PermissionKey, val number: Int, val total: Int) : OnboardingPage
    data object Finish : OnboardingPage
}

private const val INTRO_COUNT = 3

/**
 * Prise en main : trois pages d'introduction animées, une page par autorisation
 * indispensable, puis « C'est parti » qui enregistre `onboardingDone`.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val permissions = rememberPermissionController()
    val family = remember { OemSettings.family }
    val steps = remember { PermissionLogic.onboardingSteps(Build.VERSION.SDK_INT, family) }
    val pages = remember(steps) {
        buildList {
            repeat(INTRO_COUNT) { add(OnboardingPage.Intro(it)) }
            steps.forEachIndexed { i, key -> add(OnboardingPage.Permission(key, i + 1, steps.size)) }
            add(OnboardingPage.Finish)
        }
    }
    val pager = rememberPagerState { pages.size }
    val pageSpring = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    fun goTo(page: Int) {
        scope.launch {
            if (reduce) pager.scrollToPage(page) else pager.animateScrollToPage(page, animationSpec = pageSpring)
        }
    }

    BackHandler(enabled = pager.currentPage > 0) { goTo(pager.currentPage - 1) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Barre du haut : « Passer » mène directement aux autorisations.
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
                val showSkip = pager.currentPage < INTRO_COUNT
                val skipAlpha by animateFloatAsState(if (showSkip) 1f else 0f, label = "skip")
                if (skipAlpha > 0f) {
                    TextButton(
                        onClick = { goTo(INTRO_COUNT) },
                        enabled = showSkip,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .graphicsLayer { alpha = skipAlpha },
                    ) { Text(stringResource(R.string.onb_skip)) }
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1,
            ) { index ->
                val active = pager.settledPage == index
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Les pages voisines reculent légèrement et s'estompent.
                            val offset = abs((pager.currentPage - index) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                            val s = 1f - 0.08f * offset
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.5f * offset
                        },
                ) {
                    when (val page = pages[index]) {
                        is OnboardingPage.Intro -> IntroPage(page.index, active)
                        is OnboardingPage.Permission -> PermissionPage(page, permissions, family)
                        OnboardingPage.Finish -> FinishPage(
                            missing = steps.count { !permissions[it] && !PermissionLogic.isManual(it) },
                            onStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                scope.launch {
                                    SettingsRepository.update(context) { it.copy(onboardingDone = true) }
                                    onFinished()
                                }
                            },
                        )
                    }
                }
            }
            BottomBar(pager, pages, permissions, onNext = {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                goTo(pager.currentPage + 1)
            })
        }
    }
}

@Composable
private fun BottomBar(
    pager: PagerState,
    pages: List<OnboardingPage>,
    permissions: PermissionController,
    onNext: () -> Unit,
) {
    val page = pages[pager.currentPage]
    Row(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageIndicator(pager, Modifier.weight(1f))
        AnimatedVisibility(
            visible = page != OnboardingPage.Finish,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            val ready = when (page) {
                is OnboardingPage.Permission -> permissions[page.key]
                else -> true
            }
            val container by animateColorAsState(
                if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                label = "next",
            )
            val content by animateColorAsState(
                if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                label = "nextFg",
            )
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
                modifier = Modifier.animateContentSize(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)),
            ) {
                AnimatedContent(ready, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nextLabel") { ok ->
                    Text(stringResource(if (ok) R.string.onb_next else R.string.onb_later))
                }
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Indicateur expressif : le point courant s'étire en pilule, en suivant le doigt. */
@Composable
private fun PageIndicator(pager: PagerState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.onb_page_indicator, pager.currentPage + 1, pager.pageCount)
    Row(
        modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val position = pager.currentPage + pager.currentPageOffsetFraction
        repeat(pager.pageCount) { i ->
            val closeness = (1f - abs(position - i)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .height(8.dp)
                    .width(8.dp + 18.dp * closeness)
                    .clip(CircleShape)
                    .background(
                        androidx.compose.ui.graphics.lerp(scheme.outlineVariant, scheme.primary, closeness),
                    ),
            )
        }
    }
}

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun IntroPage(index: Int, active: Boolean) {
    val (title, body) = when (index) {
        0 -> R.string.onb_intro1_title to R.string.onb_intro1_body
        1 -> R.string.onb_intro2_title to R.string.onb_intro2_body
        else -> R.string.onb_intro3_title to R.string.onb_intro3_body
    }
    PageColumn {
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            when (index) {
                0 -> MorphingIslandIllustration(active)
                1 -> SplitIslandIllustration(active)
                else -> ShapeShiftIllustration(active)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.cascade(0),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.cascade(1),
        )
    }
}

@Composable
private fun PermissionPage(page: OnboardingPage.Permission, permissions: PermissionController, family: OemSettings.Family) {
    val key = page.key
    val granted = permissions[key]
    val texts = key.texts(family)
    val honor = family == OemSettings.Family.HONOR
    ConfirmHaptic(granted)
    PageColumn {
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.BottomEnd) {
            PermissionGlyph(texts.icon, granted, size = 132.dp)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(4.dp),
            ) {
                AnimatedCheck(granted, Modifier.padding(4.dp), size = 40.dp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onb_step_label, page.number, page.total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(texts.title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.cascade(0),
        )
        Spacer(Modifier.height(12.dp))
        val body = when (key) {
            PermissionKey.RESTRICTED -> R.string.perm_restricted_body
            PermissionKey.ACCESSIBILITY -> R.string.perm_accessibility_body
            PermissionKey.NOTIFICATION_ACCESS -> R.string.perm_notif_access_body
            PermissionKey.POST_NOTIFICATIONS -> R.string.perm_post_notif_body
            PermissionKey.INSTALL_PACKAGES -> R.string.perm_install_body
            PermissionKey.BATTERY -> R.string.perm_battery_body
            PermissionKey.OEM_LAUNCH -> if (honor) R.string.perm_oem_body_honor else R.string.perm_oem_body
            else -> texts.desc
        }
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.cascade(1),
        )
        val steps = when {
            key == PermissionKey.RESTRICTED -> listOf(R.string.perm_restricted_step1, R.string.perm_restricted_step2, R.string.perm_restricted_step3)
            key == PermissionKey.OEM_LAUNCH && honor -> listOf(R.string.perm_oem_step1_honor, R.string.perm_oem_step2_honor, R.string.perm_oem_step3_honor)
            else -> emptyList()
        }
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            StepList(steps.map { stringResource(it) }, Modifier.fillMaxWidth().cascade(2))
        }
        if (key == PermissionKey.ACCESSIBILITY) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.perm_accessibility_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (key == PermissionKey.BATTERY) {
            Spacer(Modifier.height(16.dp))
            HonorWarning(emphasised = honor)
        }
        Spacer(Modifier.height(24.dp))
        PermissionAction(key, granted, permissions, honor)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HonorWarning(emphasised: Boolean) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (emphasised) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.perm_battery_honor), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Bouton « Autoriser » qui laisse place à « C'est accordé », ou case « C'est fait » pour les étapes manuelles. */
@Composable
private fun PermissionAction(key: PermissionKey, granted: Boolean, permissions: PermissionController, honor: Boolean) {
    if (PermissionLogic.isManual(key)) {
        val label = when (key) {
            PermissionKey.RESTRICTED -> R.string.perm_restricted_open
            else -> if (honor) R.string.perm_oem_open_honor else R.string.perm_oem_open
        }
        FilledTonalButton(onClick = { permissions.request(key) }, modifier = Modifier.height(52.dp)) {
            Text(stringResource(label))
        }
        Spacer(Modifier.height(12.dp))
        val confirmed = permissions.isConfirmed(key) || granted
        Row(
            Modifier
                .clip(CircleShape)
                .toggleable(value = confirmed, role = Role.Checkbox) { permissions.setConfirmed(key, it) }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = confirmed, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onb_done_check), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    AnimatedContent(
        targetState = granted,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.6f))) togetherWith
                (fadeOut() + scaleOut(targetScale = 0.85f))
        },
        contentAlignment = Alignment.Center,
        label = "action",
    ) { ok ->
        if (ok) {
            Row(
                Modifier.height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedCheck(true, size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.onb_granted),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Button(
                onClick = { permissions.request(key) },
                modifier = Modifier.height(56.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(stringResource(R.string.onb_allow), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun FinishPage(missing: Int, onStart: () -> Unit) {
    val context = LocalContext.current
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    PageColumn {
        Spacer(Modifier.height(24.dp))
        IslandPreview(settings.style, settings.motion)
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.onb_finish_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.cascade(0),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onb_finish_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.cascade(1),
        )
        if (missing > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.onb_finish_missing, missing),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.cascade(2),
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .cascade(3),
        ) {
            Text(stringResource(R.string.onb_start), style = MaterialTheme.typography.titleLarge)
        }
    }
}
