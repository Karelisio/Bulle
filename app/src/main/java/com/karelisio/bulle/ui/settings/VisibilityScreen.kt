package com.karelisio.bulle.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.apps.AppCatalog
import com.karelisio.bulle.core.apps.AppEntry
import com.karelisio.bulle.core.apps.AppIcon
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.core.settings.VisibilitySettings
import com.karelisio.bulle.ui.components.cascade
import kotlinx.coroutines.launch

/** Liste d'applis en cours d'édition dans le sélecteur. */
private enum class AppTarget { HIDDEN, FULLSCREEN_IGNORED }

/**
 * Masquage automatique : plein écran (avec ses exceptions : écran d'accueil, applis choisies),
 * paysage, écran verrouillé, applis choisies.
 */
@Composable
fun VisibilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val v = settings.visibility
    var picking by remember { mutableStateOf<AppTarget?>(null) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = AppCatalog.apps(context) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun edit(t: (VisibilitySettings) -> VisibilitySettings) = scope.launch {
        SettingsRepository.update(context) { it.copy(visibility = t(it.visibility)) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.visibility_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Toggle(stringResource(R.string.visibility_fullscreen), stringResource(R.string.visibility_fullscreen_desc), v.hideInFullscreen, 0) { on -> edit { it.copy(hideInFullscreen = on) } }
                // Exceptions au plein écran : inactives tant que le masquage plein écran est coupé.
                Toggle(
                    stringResource(R.string.visibility_fullscreen_launcher),
                    stringResource(R.string.visibility_fullscreen_launcher_desc),
                    v.ignoreFullscreenOnLauncher, 1, enabled = v.hideInFullscreen,
                ) { on -> edit { it.copy(ignoreFullscreenOnLauncher = on) } }
                AppsHeader(
                    stringResource(R.string.visibility_fullscreen_apps),
                    stringResource(R.string.visibility_fullscreen_apps_desc),
                    index = 2, enabled = v.hideInFullscreen,
                ) { picking = AppTarget.FULLSCREEN_IGNORED }
            }
            items(v.fullscreenIgnoredApps.toList().sorted(), key = { "fs:$it" }) { pkg ->
                AppRow(apps.firstOrNull { it.packageName == pkg }, pkg, Modifier.animateItem()) {
                    edit { it.copy(fullscreenIgnoredApps = it.fullscreenIgnoredApps - pkg) }
                }
            }
            item {
                Toggle(stringResource(R.string.visibility_landscape_native), stringResource(R.string.visibility_landscape_native_desc), v.landscapeNative, 3) { on -> edit { it.copy(landscapeNative = on) } }
                if (!v.landscapeNative) {
                    Toggle(stringResource(R.string.visibility_landscape), null, v.hideInLandscape, 3) { on -> edit { it.copy(hideInLandscape = on) } }
                }
                Toggle(stringResource(R.string.visibility_lockscreen), null, v.hideOnLockscreen, 4) { on -> edit { it.copy(hideOnLockscreen = on) } }
                AppsHeader(stringResource(R.string.visibility_apps), stringResource(R.string.visibility_apps_desc), index = 5) {
                    picking = AppTarget.HIDDEN
                }
            }
            items(v.hiddenApps.toList().sorted(), key = { "hide:$it" }) { pkg ->
                AppRow(apps.firstOrNull { it.packageName == pkg }, pkg, Modifier.animateItem()) {
                    edit { it.copy(hiddenApps = it.hiddenApps - pkg) }
                }
            }
        }
    }
    picking?.let { target ->
        ModalBottomSheet(onDismissRequest = { picking = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AppList { app ->
                edit {
                    when (target) {
                        AppTarget.HIDDEN -> it.copy(hiddenApps = it.hiddenApps + app.packageName)
                        AppTarget.FULLSCREEN_IGNORED -> it.copy(fullscreenIgnoredApps = it.fullscreenIgnoredApps + app.packageName)
                    }
                }
                picking = null
            }
        }
    }
}

/** En-tête d'une liste d'applis, avec le bouton d'ajout. */
@Composable
private fun AppsHeader(title: String, desc: String, index: Int, enabled: Boolean = true, onAdd: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.primary) },
        supportingContent = { Text(desc) },
        trailingContent = {
            TextButton(onClick = onAdd, enabled = enabled) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.deck_add)) }
        },
        modifier = Modifier.padding(top = 12.dp).cascade(index).alpha(if (enabled) 1f else 0.5f),
    )
}

/** Une appli d'une liste, retirée par la croix. */
@Composable
private fun AppRow(app: AppEntry?, pkg: String, modifier: Modifier, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(app?.label ?: pkg) },
        leadingContent = { if (app != null) AppIcon(app, 40.dp) },
        trailingContent = {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(R.string.visibility_remove_app, app?.label ?: pkg)) }
        },
        modifier = modifier,
    )
}

@Composable
private fun Toggle(title: String, desc: String?, checked: Boolean, index: Int, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = desc?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        modifier = Modifier.cascade(index).alpha(if (enabled) 1f else 0.5f).clickable(enabled = enabled) { onChange(!checked) },
    )
}

