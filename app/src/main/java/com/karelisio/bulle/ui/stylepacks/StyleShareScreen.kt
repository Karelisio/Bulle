package com.karelisio.bulle.ui.stylepacks

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.karelisio.bulle.R
import com.karelisio.bulle.core.style.QrCode
import com.karelisio.bulle.core.style.QrMatrix
import com.karelisio.bulle.core.style.StylePack
import com.karelisio.bulle.core.style.StyleShare
import com.karelisio.bulle.ui.settings.IslandPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Partage d'un style : nom modifiable, QR code (plein écran au toucher), fichier `.bulle-style`
 * (Storage Access Framework), envoi en texte (ACTION_SEND) et copie dans le presse-papiers.
 */
@Composable
fun StyleShareScreen(pack: StylePack, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val defaultName = stringResource(R.string.stylepacks_my_style_default)
    var name by rememberSaveable { mutableStateOf(pack.name.ifBlank { defaultName }) }
    val named = remember(pack, name) { pack.copy(name = StylePack.cleanName(name) ?: defaultName) }
    val qrText = remember(named) { StyleShare.encodeQr(named) }
    val matrix = remember(qrText) { QrCode.encode(qrText) }
    var fullscreen by remember { mutableStateOf(false) }

    val msgSaved = stringResource(R.string.stylepacks_file_saved)
    val msgError = stringResource(R.string.stylepacks_file_error)
    val msgCopied = stringResource(R.string.stylepacks_copied)
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(StyleShare.MIME_TYPE)) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(StyleShare.encodeJson(named).encodeToByteArray()) }
                        ?: error("flux nul")
                }.isSuccess
            }
            snackbar.showSnackbar(if (ok) msgSaved else msgError)
        }
    }
    val shareMessage = stringResource(R.string.stylepacks_share_message, named.name, qrText)
    val chooserTitle = stringResource(R.string.stylepacks_share)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stylepacks_share_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IslandPreview(named.previewStyle(), com.karelisio.bulle.core.settings.MotionSettings(style = named.motion.style, speed = named.motion.speed), height = 150.dp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(StylePack.MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.stylepacks_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (matrix != null) {
                val qrLabel = stringResource(R.string.stylepacks_qr_label)
                Box(
                    Modifier
                        .widthIn(max = 280.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            fullscreen = true
                        }
                        .semantics { contentDescription = qrLabel }
                        .padding(18.dp),
                ) { QrImage(matrix, Modifier.fillMaxSize()) }
                Text(
                    stringResource(R.string.stylepacks_share_qr_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(stringResource(R.string.stylepacks_qr_too_long), color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { createFile.launch(StyleShare.fileName(named)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.stylepacks_share_file), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, named.name)
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.stylepacks_share_send), maxLines = 1)
                }
            }
            TextButton(onClick = {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText(named.name, qrText))
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { snackbar.showSnackbar(msgCopied) }
            }) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.stylepacks_share_copy))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (fullscreen && matrix != null) {
        QrFullscreen(matrix, named.name) { fullscreen = false }
    }
}

/** QR plein écran sur fond blanc, luminosité au maximum le temps de l'affichage. */
@Composable
private fun QrFullscreen(matrix: QrMatrix, name: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            }
            onDispose { }
        }
        Column(
            Modifier.fillMaxSize().background(Color.White).clickable(onClick = onClose).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            QrImage(matrix, Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(Modifier.height(24.dp))
            Text(name, style = MaterialTheme.typography.headlineSmall, color = Color.Black)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClose) { Text(stringResource(R.string.stylepacks_close)) }
        }
    }
}

/** Dessin d'une matrice de QR code (modules noirs sur fond transparent), chemin mis en cache. */
@Composable
fun QrImage(matrix: QrMatrix, modifier: Modifier = Modifier) {
    Box(
        modifier.drawWithCache {
            val n = matrix.size
            val cell = minOf(size.width, size.height) / n
            val ox = (size.width - cell * n) / 2
            val oy = (size.height - cell * n) / 2
            val path = Path()
            for (y in 0 until n) for (x in 0 until n) {
                if (matrix[x, y]) {
                    // Léger recouvrement : pas de fines lignes blanches entre modules voisins.
                    path.addRect(androidx.compose.ui.geometry.Rect(ox + x * cell, oy + y * cell, ox + (x + 1) * cell + 0.6f, oy + (y + 1) * cell + 0.6f))
                }
            }
            onDrawBehind { drawPath(path, Color.Black) }
        },
    )
}
