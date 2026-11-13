package com.karelisio.bulle.modules.translate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Traduction du presse-papiers dans l'île, hors ligne (ML Kit). Depuis Android 10, le presse-papiers
 * n'est lisible que par la fenêtre qui a le focus : le panneau demande la saisie ([IslandHost.requestInput])
 * puis lit le presse-papiers dès que la fenêtre de l'île est focalisée. Repli : coller dans le champ.
 */
object TranslatePanel : IslandPanel {
    override val id = "translate"

    /** Traducteur utilisé (remplaçable dans les tests). */
    @Volatile
    internal var engine: TranslationEngine = MlKitEngine

    @Composable
    override fun Content(host: IslandHost) = TranslatePanelContent(host, engine) { readClipboard(host.context) }

    internal fun readClipboard(context: Context): String? = runCatching {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
        clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

/** État de la traduction affichée. */
internal sealed interface TranslateStatus {
    data object Idle : TranslateStatus
    data object Working : TranslateStatus
    data class Done(val text: String) : TranslateStatus
    data class Missing(val languages: List<String>) : TranslateStatus
    data class Downloading(val languages: List<String>, val waitingWifi: Boolean) : TranslateStatus
    data object Failed : TranslateStatus
}

private enum class Picking { SOURCE, TARGET }

private fun onUnmeteredNetwork(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
}.getOrDefault(false)

@Composable
internal fun TranslatePanelContent(host: IslandHost, engine: TranslationEngine, clipboard: () -> String?) {
    val context = host.context
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val settings by remember(context) { TranslatePrefs.flow(context) }.collectAsState(TranslateSettings())

    var text by remember { mutableStateOf("") }
    var clipboardRead by remember { mutableStateOf(false) }
    var manualSource by remember { mutableStateOf<String?>(null) }
    var detected by remember { mutableStateOf<String?>(null) }
    var manualTarget by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<TranslateStatus>(TranslateStatus.Idle) }
    var modelsVersion by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<Picking?>(null) }
    var copied by remember { mutableStateOf(false) }

    val supported = remember(engine) { engine.languages }
    val target = manualTarget ?: TranslateLanguages.target(settings, supported)
    val source = manualSource ?: detected
    val effectiveTarget = TranslateLanguages.resolveTarget(source, target)

    // Fenêtre focalisable, puis lecture du presse-papiers une fois le focus obtenu.
    DisposableEffect(view) {
        host.requestInput(true)
        fun read() {
            if (clipboardRead) return
            clipboardRead = true
            clipboard()?.let { if (text.isEmpty()) text = it }
        }
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) read() }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        if (view.hasWindowFocus()) read()
        onDispose {
            runCatching { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
            host.requestInput(false)
        }
    }
    // Filet de sécurité : certains OEM ne notifient pas le focus ; on tente une lecture après un délai.
    LaunchedEffect(Unit) {
        delay(700)
        if (!clipboardRead) {
            clipboardRead = true
            clipboard()?.let { if (text.isEmpty()) text = it }
        }
    }

    // Détection de la langue puis traduction (après une courte pause de frappe).
    LaunchedEffect(text, manualSource, target, modelsVersion) {
        if (text.isBlank()) {
            detected = null
            status = TranslateStatus.Idle
            return@LaunchedEffect
        }
        delay(250)
        status = TranslateStatus.Working
        try {
            val src = manualSource ?: engine.identify(text).also { detected = it }
            if (src == null) {
                status = TranslateStatus.Failed
                return@LaunchedEffect
            }
            val tgt = TranslateLanguages.resolveTarget(src, target)
            status = TranslateStatus.Done(engine.translate(text, src, tgt))
        } catch (e: MissingModelException) {
            status = TranslateStatus.Missing(e.languages)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = TranslateStatus.Failed
        }
    }
    LaunchedEffect(copied) { if (copied) { delay(1_500); copied = false } }

    fun download(languages: List<String>) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            status = TranslateStatus.Downloading(languages, waitingWifi = settings.wifiOnly && !onUnmeteredNetwork(context))
            try {
                languages.forEach { engine.download(it, settings.wifiOnly) }
                modelsVersion++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = TranslateStatus.Failed
            }
        }
    }

    val sizeSpring = if (reduceMotion) snap() else spring<androidx.compose.ui.unit.IntSize>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    Column(Modifier.fillMaxWidth().animateContentSize(sizeSpring)) {
        // Langues : source (détectée) ⇄ cible.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LanguagePill(
                label = source?.let { TranslateLanguages.displayName(it) } ?: stringResource(R.string.translate_detect),
                hint = if (manualSource == null && detected != null) stringResource(R.string.translate_detected) else null,
                selected = picking == Picking.SOURCE,
                modifier = Modifier.weight(1f),
            ) { picking = if (picking == Picking.SOURCE) null else Picking.SOURCE }
            PanelIcon(Icons.Rounded.SwapHoriz, stringResource(R.string.translate_swap), enabled = source != null) {
                val done = status as? TranslateStatus.Done
                val newSource = effectiveTarget
                manualTarget = source
                manualSource = newSource
                if (done != null) text = done.text
            }
            LanguagePill(
                label = TranslateLanguages.displayName(effectiveTarget),
                hint = null,
                selected = picking == Picking.TARGET,
                modifier = Modifier.weight(1f),
            ) { picking = if (picking == Picking.TARGET) null else Picking.TARGET }
        }
        AnimatedVisibility(
            visible = picking != null,
            enter = fadeIn() + expandVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val current = if (picking == Picking.SOURCE) source else effectiveTarget
                TranslateLanguages.ordered(supported).forEach { code ->
                    LanguagePill(TranslateLanguages.displayName(code), null, selected = code == current) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        if (picking == Picking.SOURCE) manualSource = code else manualTarget = code
                        picking = null
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Texte source : prérempli depuis le presse-papiers, modifiable (coller à la main en repli).
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (text.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.translate_paste_hint), color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it; manualSource = null },
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.85f)),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))

        // Résultat ou état (modèle manquant, téléchargement, erreur).
        when (val s = status) {
            is TranslateStatus.Done -> Text(
                s.text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
            is TranslateStatus.Missing -> Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    stringResource(R.string.translate_missing_model, s.languages.joinToString { TranslateLanguages.displayName(it) }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Text(
                    stringResource(if (settings.wifiOnly) R.string.translate_missing_wifi else R.string.translate_missing_any),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(8.dp))
                ActionPill(Icons.Rounded.Download, stringResource(R.string.translate_download)) { download(s.languages) }
            }
            is TranslateStatus.Downloading -> Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    stringResource(
                        if (s.waitingWifi) R.string.translate_waiting_wifi else R.string.translate_downloading,
                        s.languages.joinToString { TranslateLanguages.displayName(it) },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                if (!reduceMotion) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
            TranslateStatus.Working -> Text("…", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 4.dp))
            TranslateStatus.Failed -> Text(
                stringResource(if (text.isBlank()) R.string.translate_empty else R.string.translate_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            TranslateStatus.Idle -> if (clipboardRead && text.isBlank()) {
                Text(stringResource(R.string.translate_empty), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 4.dp))
            }
        }

        // Actions : copier la traduction, inverser, changer de langue.
        val done = status as? TranslateStatus.Done
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            ActionPill(Icons.Rounded.Translate, stringResource(R.string.translate_change_language)) {
                picking = if (picking == null) Picking.TARGET else null
            }
            if (done != null) {
                ActionPill(if (copied) Icons.Rounded.Done else Icons.Rounded.ContentCopy, stringResource(if (copied) R.string.translate_copied else R.string.translate_copy)) {
                    runCatching {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Bulle", done.text))
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    copied = true
                }
            }
        }
    }
}

@Composable
private fun LanguagePill(label: String, hint: String?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f), maxLines = 1) }
    }
}

@Composable
private fun PanelIcon(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(8.dp),
    ) {
        Icon(icon, contentDescription = description, tint = Color.White.copy(alpha = if (enabled) 0.85f else 0.3f))
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
    }
}
