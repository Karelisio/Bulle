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

/** Masquage automatique : plein écran, paysage, écran verrouillé, applis choisies. */
@Composable
fun VisibilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val v = settings.visibility
    var picking by remember { mutableStateOf(false) }
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
                Toggle(stringResource(R.string.visibility_landscape), null, v.hideInLandscape, 1) { on -> edit { it.copy(hideInLandscape = on) } }
                Toggle(stringResource(R.string.visibility_lockscreen), null, v.hideOnLockscreen, 2) { on -> edit { it.copy(hideOnLockscreen = on) } }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.visibility_apps), color = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(stringResource(R.string.visibility_apps_desc)) },
                    trailingContent = {
                        TextButton(onClick = { picking = true }) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.deck_add)) }
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(v.hiddenApps.toList().sorted(), key = { it }) { pkg ->
                val app = apps.firstOrNull { it.packageName == pkg }
                ListItem(
                    headlineContent = { Text(app?.label ?: pkg) },
                    leadingContent = { if (app != null) AppIcon(app, 40.dp) },
                    trailingContent = {
                        IconButton(onClick = { edit { it.copy(hiddenApps = it.hiddenApps - pkg) } }) { Icon(Icons.Rounded.Close, null) }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AppList { app ->
                edit { it.copy(hiddenApps = it.hiddenApps + app.packageName) }
                picking = false
            }
        }
    }
}

@Composable
private fun Toggle(title: String, desc: String?, checked: Boolean, index: Int, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = desc?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.cascade(index).clickable { onChange(!checked) },
    )
}

