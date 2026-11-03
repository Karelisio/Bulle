package com.karelisio.bulle.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.actions.GestureSettings
import com.karelisio.bulle.core.apps.AppCatalog
import com.karelisio.bulle.core.apps.AppEntry
import com.karelisio.bulle.core.apps.AppIcon
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import kotlinx.coroutines.launch

private enum class Picker { ACTION, APP, SHORTCUT }

/** Réglage des gestes : chaque geste → une action. */
@Composable
fun GesturesSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var editing by remember { mutableStateOf<IslandGesture?>(null) }
    var picker by remember { mutableStateOf(Picker.ACTION) }

    fun save(gesture: IslandGesture, action: GestureAction) {
        if (action.type.needsWriteSettings && !Settings.System.canWrite(context)) {
            Toast.makeText(context, R.string.write_settings_needed, Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
        }
        scope.launch { SettingsRepository.update(context) { it.copy(gestures = it.gestures.with(gesture, action)) } }
        editing = null
    }

    // Raccourci d'appli : l'activité ACTION_CREATE_SHORTCUT renvoie l'intent du raccourci.
    val shortcutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val gesture = editing ?: return@rememberLauncherForActivityResult
        val data = result.data
        val target: Intent? = data?.let { IntentCompat.getParcelableExtra(it, Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java) }
        @Suppress("DEPRECATION")
        val name = data?.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            save(gesture, GestureAction(ActionType.LAUNCH_SHORTCUT, intentUri = target.toUri(Intent.URI_INTENT_SCHEME), label = name))
        } else if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, R.string.shortcut_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.gestures_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            items(GestureSettings.configurable) { gesture ->
                val action = settings.gestures.actionFor(gesture) ?: GestureAction()
                ListItem(
                    headlineContent = { Text(stringResource(gesture.labelRes())) },
                    supportingContent = {
                        AnimatedContent(action, transitionSpec = {
                            (fadeIn() + slideInVertically { it / 2 }).togetherWith(fadeOut())
                        }, label = "action") { Text(it.label(), color = MaterialTheme.colorScheme.primary) }
                    },
                    leadingContent = { Icon(gesture.icon(), contentDescription = null) },
                    modifier = Modifier.clickable { picker = Picker.ACTION; editing = gesture },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
            item {
                Text(
                    stringResource(R.string.gesture_swipe_up_note) + "\n" + stringResource(R.string.gesture_double_tap_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }

    val gesture = editing ?: return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = picker != Picker.ACTION)
    ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = sheet) {
        when (picker) {
            Picker.ACTION -> ActionList(current = settings.gestures.actionFor(gesture)?.type) { type ->
                when (type) {
                    ActionType.OPEN_APP -> picker = Picker.APP
                    ActionType.LAUNCH_SHORTCUT -> picker = Picker.SHORTCUT
                    else -> save(gesture, GestureAction(type))
                }
            }
            Picker.APP -> AppList { app ->
                save(gesture, GestureAction(ActionType.OPEN_APP, packageName = app.packageName, label = app.label))
            }
            Picker.SHORTCUT -> ShortcutList { component ->
                shortcutLauncher.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(component))
            }
        }
    }
}

private fun IslandGesture.icon(): ImageVector = when (this) {
    IslandGesture.TAP -> Icons.Rounded.TouchApp
    IslandGesture.DOUBLE_TAP -> Icons.Rounded.AdsClick
    IslandGesture.LONG_PRESS -> Icons.Rounded.BackHand
    IslandGesture.SWIPE_LEFT -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
    IslandGesture.SWIPE_RIGHT -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
    else -> Icons.Rounded.KeyboardArrowDown
}

@Composable
private fun ActionList(current: ActionType?, onPick: (ActionType) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(
                stringResource(R.string.action_pick_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        ActionGroups.forEach { (titleRes, types) ->
            item {
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(types) { type ->
                ListItem(
                    headlineContent = { Text(stringResource(type.labelRes())) },
                    leadingContent = { RadioButton(selected = type == current, onClick = { onPick(type) }) },
                    modifier = Modifier.clickable { onPick(type) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
            item { HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
        }
    }
}

@Composable
private fun AppList(onPick: (AppEntry) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = AppCatalog.apps(context) }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(
                stringResource(R.string.pick_app_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        items(apps, key = { it.component.flattenToShortString() }) { app ->
            ListItem(
                headlineContent = { Text(app.label) },
                leadingContent = { AppIcon(app, 40.dp) },
                modifier = Modifier.clickable { onPick(app) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
    }
}

@Composable
private fun ShortcutList(onPick: (ComponentName) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val entries = remember {
        pm.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0)
            .map { Triple(it.loadLabel(pm).toString(), ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.activityInfo.packageName) }
            .sortedBy { it.first.lowercase() }
    }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(
                stringResource(R.string.pick_shortcut_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        items(entries, key = { it.second.flattenToShortString() }) { (label, component, pkg) ->
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = {
                    Box(Modifier.size(40.dp)) {
                        AppIcon(AppEntry(label, pkg, component), 40.dp)
                    }
                },
                trailingContent = { Icon(Icons.Rounded.Shortcut, contentDescription = null) },
                modifier = Modifier.clickable { onPick(component) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
    }
}
