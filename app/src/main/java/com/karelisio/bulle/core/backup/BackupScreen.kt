package com.karelisio.bulle.core.backup

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.ui.components.cascade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** Écran Sauvegarde : export / import JSON, sauvegarde automatique et restauration. */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var autoBackups by remember { mutableStateOf<List<AutoBackup>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { autoBackups = withContext(Dispatchers.IO) { BackupStore.list(context) } }

    // Sauvegarde validée en attente de confirmation.
    var pending by remember { mutableStateOf<BackupEnvelope?>(null) }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    fun handle(result: DecodeResult) {
        when (result) {
            is DecodeResult.Ok -> pending = result.envelope
            DecodeResult.InvalidJson -> message(res.getString(R.string.backup_error_json))
            DecodeResult.NotABackup -> message(res.getString(R.string.backup_error_format))
            is DecodeResult.UnsupportedSchema -> message(res.getString(R.string.backup_error_schema))
        }
    }

    suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: IOException) {
            message(res.getString(R.string.backup_error_io, e.message ?: e.javaClass.simpleName))
        } catch (e: SecurityException) {
            message(res.getString(R.string.backup_error_io, e.message ?: e.javaClass.simpleName))
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            guarded {
                BackupStore.exportTo(context, uri)
                message(res.getString(R.string.backup_exported))
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { guarded { handle(BackupStore.readFrom(context, uri)) } }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
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
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "export") {
                ActionCard(
                    icon = Icons.Rounded.FileUpload,
                    title = stringResource(R.string.backup_export),
                    desc = stringResource(R.string.backup_export_desc),
                    modifier = Modifier.cascade(0),
                    onClick = { exportLauncher.launch(BackupCodec.exportFileName(System.currentTimeMillis())) },
                )
            }
            item(key = "import") {
                ActionCard(
                    icon = Icons.Rounded.FileDownload,
                    title = stringResource(R.string.backup_import),
                    desc = stringResource(R.string.backup_import_desc),
                    modifier = Modifier.cascade(1),
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
            item(key = "auto") {
                Card(
                    modifier = Modifier.fillMaxWidth().cascade(2),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.backup_auto)) },
                        supportingContent = { Text(stringResource(R.string.backup_auto_desc)) },
                        leadingContent = { Icon(Icons.Rounded.History, null) },
                        trailingContent = {
                            Switch(
                                checked = settings.backup.autoBackup,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        SettingsRepository.update(context) { it.copy(backup = it.backup.copy(autoBackup = on)) }
                                    }
                                },
                            )
                        },
                        modifier = Modifier.clickable {
                            scope.launch {
                                SettingsRepository.update(context) {
                                    it.copy(backup = it.backup.copy(autoBackup = !it.backup.autoBackup))
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (settings.backup.lastAutoBackup > 0) {
                                stringResource(R.string.backup_last_auto, formatDate(context, settings.backup.lastAutoBackup))
                            } else {
                                ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(onClick = {
                            scope.launch {
                                guarded {
                                    BackupStore.backupNow(context)
                                    refresh++
                                    message(res.getString(R.string.backup_saved))
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.backup_now))
                        }
                    }
                }
            }
            item(key = "list-title") {
                Text(
                    stringResource(R.string.backup_auto_list),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp).cascade(3),
                )
            }
            if (autoBackups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.backup_auto_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).cascade(4),
                    )
                }
            }
            itemsIndexed(autoBackups, key = { _, b -> b.file.name }) { index, backup ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().animateItem().cascade(4 + index),
                ) {
                    ListItem(
                        headlineContent = { Text(formatDate(context, backup.createdAt)) },
                        leadingContent = { Icon(Icons.Rounded.CloudDone, null, tint = MaterialTheme.colorScheme.tertiary) },
                        trailingContent = {
                            TextButton(onClick = {
                                scope.launch { guarded { handle(BackupStore.readFrom(backup.file)) } }
                            }) {
                                Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.backup_restore))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
            }
        }
    }

    pending?.let { envelope ->
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = { Icon(Icons.Rounded.Restore, null) },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_confirm_text,
                        envelope.appVersion.ifBlank { stringResource(R.string.backup_unknown_version) },
                        if (envelope.exportedAt > 0) formatDate(context, envelope.exportedAt) else "—",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        BackupStore.apply(context, envelope)
                        message(res.getString(R.string.backup_restored))
                    }
                }) { Text(stringResource(R.string.backup_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.backup_cancel)) }
            },
        )
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(8.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun formatDate(context: Context, epochMs: Long): String {
    val locale = context.resources.configuration.locales[0]
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(epochMs))
}
