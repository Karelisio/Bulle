package com.karelisio.bulle.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.NoAccounts
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.karelisio.bulle.BuildConfig
import com.karelisio.bulle.R
import com.karelisio.bulle.core.update.MarkdownText
import com.karelisio.bulle.core.update.UpdateChecker
import com.karelisio.bulle.core.update.UpdateResult
import com.karelisio.bulle.ui.components.MorphShape
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** État du bouton « Rechercher une mise à jour ». */
private sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data class Done(val result: UpdateResult) : CheckState
}

/** Écran À propos : version, mises à jour, historique, autorisations et vie privée. */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var check by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var changelog by remember { mutableStateOf<List<ChangelogSection>?>(null) }
    var changelogMissing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val text = withContext(Dispatchers.IO) {
            try {
                context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                null
            }
        }
        changelog = text?.let { Changelog.sections(it) }
        changelogMissing = text == null
    }
    // Relit l'état des autorisations au retour des pages système.
    var permTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permTick++ }
    var expanded by remember { mutableStateOf(setOf(0)) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { Header(Modifier.cascade(0)) }
            item(key = "update") {
                UpdateCard(
                    state = check,
                    modifier = Modifier.cascade(1),
                    onCheck = {
                        check = CheckState.Checking
                        scope.launch { check = CheckState.Done(UpdateChecker.checkNow(context.applicationContext)) }
                    },
                    onGithub = { openUrl(context, "https://github.com/${BuildConfig.GITHUB_REPO}") },
                )
            }
            item(key = "privacy") { PrivacyCard(Modifier.cascade(2)) }

            item(key = "changelog-title") { SectionTitle(stringResource(R.string.about_changelog), Modifier.cascade(3)) }
            val sections = changelog
            if (sections.isNullOrEmpty()) {
                if (changelogMissing || sections != null) {
                    item(key = "changelog-missing") {
                        Text(
                            stringResource(R.string.about_changelog_missing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            } else {
                itemsIndexed(sections, key = { i, s -> "cl-$i-${s.title}" }) { index, section ->
                    ChangelogItem(
                        section = section,
                        expanded = index in expanded,
                        modifier = Modifier.cascade(4 + index),
                        onToggle = { expanded = if (index in expanded) expanded - index else expanded + index },
                    )
                }
            }

            item(key = "perm-title") {
                Row(
                    Modifier.fillMaxWidth().cascade(5),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(stringResource(R.string.about_permissions), Modifier.weight(1f))
                    TextButton(onClick = onOpenPermissions) { Text(stringResource(R.string.about_permissions_manage)) }
                }
            }
            itemsIndexed(bullePermissions, key = { _, p -> "perm-${p.id}" }) { index, permission ->
                PermissionItem(permission, permTick, Modifier.cascade(6 + index))
            }
        }
    }
}

/** Bulle animée (cercle ⇄ éclat doux) + nom et version. */
@Composable
private fun Header(modifier: Modifier) {
    val reduce = rememberReduceMotion()
    val progress = if (reduce) 0f else {
        val transition = rememberInfiniteTransition(label = "about-bubble")
        val p by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
            label = "morph",
        )
        p
    }
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.SoftBurst) }
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
            Box(
                Modifier
                    .size(124.dp)
                    .graphicsLayer {
                        val s = 1f + 0.05f * progress
                        scaleX = s; scaleY = s
                    }
                    .rotate(60f * progress)
                    .clip(MorphShape(morph, progress))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            // L'île elle-même : une pilule noire.
            Box(
                Modifier
                    .width(64.dp + 10.dp * progress)
                    .height(22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun UpdateCard(state: CheckState, modifier: Modifier, onCheck: () -> Unit, onGithub: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { (fadeIn() + slideInVertically { it / 3 }).togetherWith(fadeOut()) },
                label = "check",
            ) { s ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    when (s) {
                        CheckState.Idle -> StatusLine(Icons.Rounded.NewReleases, stringResource(R.string.about_check_update), MaterialTheme.colorScheme.primary)
                        CheckState.Checking -> {
                            if (rememberReduceMotion()) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            } else {
                                LoadingIndicator(Modifier.size(36.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.about_checking), style = MaterialTheme.typography.titleSmall)
                        }
                        is CheckState.Done -> when (val r = s.result) {
                            UpdateResult.UpToDate -> StatusLine(
                                Icons.Rounded.CheckCircle, stringResource(R.string.about_up_to_date), MaterialTheme.colorScheme.primary,
                            )
                            is UpdateResult.Available -> StatusLine(
                                Icons.Rounded.NewReleases,
                                stringResource(R.string.about_update_available, r.release.version),
                                MaterialTheme.colorScheme.tertiary,
                            )
                            is UpdateResult.Error -> StatusLine(
                                Icons.Rounded.ErrorOutline,
                                stringResource(R.string.about_update_error, r.message),
                                MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onCheck, enabled = state != CheckState.Checking, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.about_check_update), maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onGithub, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Code, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.about_github))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color) {
    Icon(icon, null, tint = color)
    Spacer(Modifier.width(12.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = color)
}

@Composable
private fun PrivacyCard(modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.about_privacy_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            PrivacyLine(Icons.Rounded.PhoneAndroid, stringResource(R.string.about_privacy_local))
            PrivacyLine(Icons.Rounded.NoAccounts, stringResource(R.string.about_privacy_account))
            PrivacyLine(Icons.Rounded.TrackChanges, stringResource(R.string.about_privacy_analytics))
        }
    }
}

@Composable
private fun PrivacyLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ChangelogItem(section: ChangelogSection, expanded: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(MaterialShapes.Cookie4Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(12.dp))
                Text(section.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.rotate(rotation))
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                MarkdownText(section.body, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
            }
        }
    }
}

@Composable
private fun PermissionItem(permission: BullePermission, tick: Int, modifier: Modifier) {
    val context = LocalContext.current
    val granted = remember(tick) { runCatching { permission.granted(context) }.getOrNull() }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(permission.title)) },
                supportingContent = { Text(stringResource(permission.why)) },
                leadingContent = {
                    Icon(
                        permission.icon, null,
                        tint = if (granted == false) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            permission.adbCommand?.let { command ->
                val text = command(context)
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        )
                        IconButton(onClick = { copy(context, text) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.about_copy))
                        }
                    }
                }
            }
            if (granted != null || permission.settingsIntent != null) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (granted != null) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    stringResource(if (granted) R.string.about_permission_granted else R.string.about_permission_denied),
                                )
                            },
                            leadingIcon = {
                                Icon(if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp))
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (permission.settingsIntent != null) {
                        FilledTonalButton(onClick = { openPermissionSettings(context, permission) }) {
                            Text(stringResource(R.string.about_permission_open))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("adb", text))
    Toast.makeText(context, R.string.about_copied, Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
