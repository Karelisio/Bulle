package com.karelisio.bulle.ui.stylepacks

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.MotionSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.core.style.BuiltInPacks
import com.karelisio.bulle.core.style.MyStylesStore
import com.karelisio.bulle.core.style.SavedStyle
import com.karelisio.bulle.core.style.SavedStylesOps
import com.karelisio.bulle.core.style.StylePack
import com.karelisio.bulle.core.style.StyleShare
import com.karelisio.bulle.ui.settings.IslandPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran « Packs de styles » : carrousel des packs intégrés (aperçus animés), « Mes styles »
 * (enregistrer, renommer, supprimer, appliquer avec annulation), import (fichier, texte, QR)
 * et partage.
 *
 * [onScanQr] : lanceur du scanner de QR (fourni par l'orchestrateur, ex. `QrScan.launch(context, "style")`) ;
 * null = bouton masqué. [importRequest] : texte scanné à importer (ex. dernier résultat de
 * `QrScan.results` pour ce tag), décodé avec [StyleShare.decode] puis proposé à l'utilisateur ;
 * [onImportRequestConsumed] est appelé une fois le texte pris en compte.
 */
@Composable
fun StylePacksScreen(
    onBack: () -> Unit,
    // TODO(orchestrateur) : brancher QrScan.launch(context, tag) ici et QrScan.results → importRequest.
    onScanQr: (() -> Unit)? = null,
    importRequest: String? = null,
    onImportRequestConsumed: () -> Unit = {},
) {
    var sharing by remember { mutableStateOf<StylePack?>(null) }
    AnimatedContent(sharing, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stylepacks") { shared ->
        if (shared != null) {
            BackHandler { sharing = null }
            StyleShareScreen(shared, onBack = { sharing = null })
        } else {
            StylePacksContent(onBack, onScanQr, importRequest, onImportRequestConsumed, onShare = { sharing = it })
        }
    }
}

@Composable
private fun StylePacksContent(
    onBack: () -> Unit,
    onScanQr: (() -> Unit)?,
    importRequest: String?,
    onImportRequestConsumed: () -> Unit,
    onShare: (StylePack) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val saved by remember { MyStylesStore.flow(context) }.collectAsStateWithLifecycle(emptyList())
    val current = remember(settings) { StylePack.capture(settings) }

    val undoLabel = stringResource(R.string.stylepacks_undo)
    val appliedFmt = stringResource(R.string.stylepacks_applied)
    val deletedFmt = stringResource(R.string.stylepacks_deleted)
    val savedFmt = stringResource(R.string.stylepacks_saved)
    val invalidMsg = stringResource(R.string.stylepacks_import_invalid)
    val importedName = stringResource(R.string.stylepacks_import_default_name)

    /** Applique un style ; la snackbar permet de revenir au style précédent (position intacte). */
    fun apply(pack: StylePack, label: String) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            val before = StylePack.capture(SettingsRepository.current(context))
            SettingsRepository.update(context) { pack.applyTo(it) }
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(appliedFmt.format(label), actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) SettingsRepository.update(context) { before.applyTo(it) }
        }
    }

    fun saveToMine(pack: StylePack) {
        scope.launch {
            MyStylesStore.update(context) { SavedStylesOps.add(it, pack) }
            snackbar.showSnackbar(savedFmt.format(pack.name))
        }
    }

    // Import : un style décodé attend la confirmation de l'utilisateur.
    var pendingImport by remember { mutableStateOf<StylePack?>(null) }
    fun receive(text: String?) {
        val pack = text?.let(StyleShare::decode)
        if (pack == null) scope.launch { snackbar.showSnackbar(invalidMsg) }
        else pendingImport = pack.copy(name = pack.name.ifBlank { importedName })
    }
    LaunchedEffect(importRequest) {
        if (importRequest != null) {
            receive(importRequest)
            onImportRequestConsumed()
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // Lecture bornée : un fichier énorme est refusé sans tout charger.
                        val bytes = input.readNBytesCompat(StyleShare.MAX_BYTES + 1)
                        if (bytes.size > StyleShare.MAX_BYTES) null else bytes.decodeToString()
                    }
                }.getOrNull()
            }
            receive(text)
        }
    }

    var saveDialog by rememberSaveable { mutableStateOf(false) }
    var pasteDialog by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedStyle?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stylepacks_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // --- Packs intégrés : carrousel Material 3 Expressive ---
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.stylepacks_builtin), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.stylepacks_builtin_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BuiltInCarousel(current) { pack, label -> apply(pack, label) }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // --- Mes styles ---
                PackSection(stringResource(R.string.stylepacks_mine), 0) {
                    if (saved.isEmpty()) {
                        Text(stringResource(R.string.stylepacks_mine_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                    saved.forEach { item ->
                        SavedRow(
                            item = item,
                            isCurrent = item.pack.sameLookAs(current),
                            onApply = { apply(item.pack, item.pack.name) },
                            onRename = { renaming = item },
                            onShare = { onShare(item.pack) },
                            onDelete = {
                                scope.launch {
                                    val index = saved.indexOf(item)
                                    MyStylesStore.update(context) { SavedStylesOps.remove(it, item.id) }
                                    val r = snackbar.showSnackbar(deletedFmt.format(item.pack.name), actionLabel = undoLabel, duration = SnackbarDuration.Short)
                                    if (r == SnackbarResult.ActionPerformed) MyStylesStore.update(context) { SavedStylesOps.restore(it, item, index) }
                                }
                            },
                        )
                    }
                    FilledTonalButton(onClick = { saveDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.stylepacks_save_current))
                    }
                }
                // --- Partage et import ---
                PackSection(stringResource(R.string.stylepacks_import), 1) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.stylepacks_share_current)) },
                        supportingContent = { Text(stringResource(R.string.stylepacks_share_current_hint)) },
                        leadingContent = { Icon(Icons.Rounded.QrCode2, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickableRow { onShare(current) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.stylepacks_import_file), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = { pasteDialog = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.stylepacks_import_text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (onScanQr != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.stylepacks_import_qr))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    val defaultName = stringResource(R.string.stylepacks_my_style_default)
    if (saveDialog) {
        NameDialog(
            title = stringResource(R.string.stylepacks_save_current),
            initial = defaultName,
            onDismiss = { saveDialog = false },
            onConfirm = { name ->
                saveDialog = false
                saveToMine(current.copy(name = name))
            },
        )
    }
    renaming?.let { item ->
        NameDialog(
            title = stringResource(R.string.stylepacks_rename),
            initial = item.pack.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                scope.launch { MyStylesStore.update(context) { SavedStylesOps.rename(it, item.id, name) } }
            },
        )
    }
    if (pasteDialog) {
        PasteDialog(onDismiss = { pasteDialog = false }) { text ->
            pasteDialog = false
            receive(text)
        }
    }
    pendingImport?.let { pack ->
        ImportDialog(
            pack = pack,
            onDismiss = { pendingImport = null },
            onApply = {
                pendingImport = null
                apply(pack, pack.name)
            },
            onSave = {
                pendingImport = null
                saveToMine(pack)
            },
        )
    }
}

/** Carrousel des packs intégrés : chaque élément montre l'île animée dans le style du pack. */
@Composable
private fun BuiltInCarousel(current: StylePack, onApply: (StylePack, String) -> Unit) {
    val packs = BuiltInPacks.all
    val state = rememberCarouselState { packs.size }
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 250.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(232.dp),
    ) { i ->
        val entry = packs[i]
        val (nameRes, descRes) = builtInLabels(entry.id)
        val name = stringResource(nameRes)
        val selected = entry.pack.sameLookAs(current)
        Box(
            Modifier
                .fillMaxSize()
                .maskClip(RoundedCornerShape(28.dp))
                .clickableRow { onApply(entry.pack, name) },
        ) {
            IslandPreview(
                entry.pack.previewStyle(),
                MotionSettings(style = entry.pack.motion.style, speed = entry.pack.motion.speed, blurTransitions = entry.pack.motion.blurTransitions),
                height = 232.dp,
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (selected) {
                        Spacer(Modifier.size(6.dp))
                        Icon(Icons.Rounded.CheckCircle, stringResource(R.string.stylepacks_current), tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SavedRow(
    item: SavedStyle,
    isCurrent: Boolean,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(item.pack.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (isCurrent) ({ Text(stringResource(R.string.stylepacks_current)) }) else null,
        leadingContent = { PackSwatch(item.pack) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.stylepacks_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.stylepacks_apply)) }, onClick = { menu = false; onApply() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.stylepacks_rename)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.stylepacks_share)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = { menu = false; onShare() },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.stylepacks_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow(onApply),
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val clean = StylePack.cleanName(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(StylePack.MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.stylepacks_name)) },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { clean?.let(onConfirm) }, enabled = clean != null) { Text(stringResource(R.string.stylepacks_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.stylepacks_cancel)) } },
    )
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stylepacks_import_text)) },
        text = {
            Column {
                Text(stringResource(R.string.stylepacks_import_text_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it.take(StyleShare.MAX_BYTES) }, minLines = 3, maxLines = 6)
            }
        },
        confirmButton = { Button(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.stylepacks_import_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.stylepacks_cancel)) } },
    )
}

/** Confirmation d'un style reçu : aperçu, puis appliquer ou ajouter à « Mes styles ». */
@Composable
private fun ImportDialog(pack: StylePack, onDismiss: () -> Unit, onApply: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stylepacks_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pack.name, style = MaterialTheme.typography.titleMedium)
                IslandPreview(
                    pack.previewStyle(),
                    MotionSettings(style = pack.motion.style, speed = pack.motion.speed, blurTransitions = pack.motion.blurTransitions),
                    height = 150.dp,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSave) { Text(stringResource(R.string.stylepacks_import_save)) }
                Button(onClick = onApply) { Text(stringResource(R.string.stylepacks_apply)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.stylepacks_cancel)) } },
    )
}

/** Ligne cliquable avec retour visuel standard. */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

/** Lit au plus [limit] octets (InputStream.readNBytes n'existe qu'à partir d'Android 13). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(4096)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
