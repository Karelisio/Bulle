package com.karelisio.bulle.modules.live

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.modules.live.ui.LiveProgressBar
import com.karelisio.bulle.modules.live.ui.ProgressRing
import com.karelisio.bulle.modules.live.ui.RollingText
import com.karelisio.bulle.notifications.NotificationIntents
import com.karelisio.bulle.ui.components.rememberReduceMotion

/**
 * Tous les téléchargements en cours, réunis dans un seul contenu de clé fixe
 * ([LiveDownloads.KEY]) pour que l'île étendue ne se replie pas quand un téléchargement
 * arrive, se termine ou passe au premier plan.
 *
 * Avec un seul téléchargement, l'affichage est exactement celui d'une [LiveContent] ;
 * à partir de deux : icône animée + progression globale en compact, liste en étendu.
 *
 * @param items téléchargements triés par heure de début ([LiveDownloads.sorted]), non vide.
 * @param countLabel « N téléchargements », déjà localisé.
 */
data class LiveDownloadsContent(
    val items: List<LiveContent>,
    val countLabel: String,
) : IslandContent() {
    init {
        require(items.isNotEmpty()) { "Aucun téléchargement" }
    }

    /** Seul téléchargement : rendu délégué à son contenu habituel. */
    private val single: LiveContent? = items.singleOrNull()

    /** Progression globale (anneau et pourcentage). */
    val overall: LiveProgress? = LiveDownloads.overall(items.map { it.activity })

    override val key = LiveDownloads.KEY
    override val moduleId = LiveModule.ID
    override val priority = Priority.LIVE
    override val postedAt: Long = items.minOf { it.postedAt }
    override val notificationKey: String? = single?.notificationKey

    /** Accent commun si toutes les notifications ont la même couleur, sinon l'accent par défaut. */
    override val accent: Color = single?.accent
        ?: items.map { it.activity.color }.distinct().singleOrNull()?.let { LiveContent.accentOf(it) }
        ?: LiveContent.DefaultAccent

    override val compactSubtitle: String? = if (single != null) single.compactSubtitle else countLabel

    override val compactSlotWidth: Dp = single?.compactSlotWidth
        ?: if (overall?.indeterminate == false) 68.dp else 40.dp

    // ---------- Vue compacte ----------

    @Composable
    override fun Leading() {
        val s = single
        if (s != null) s.Leading() else DownloadGlyph(accent, 22.dp)
    }

    @Composable
    override fun Trailing() {
        val s = single
        if (s != null) return s.Trailing()
        val p = overall ?: return
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!p.indeterminate) {
                RollingText(
                    "${LiveFormat.percent(p.fraction)}",
                    style = MaterialTheme.typography.labelMedium.merge(CompactDigits),
                    color = accent,
                )
                Spacer(Modifier.width(4.dp))
            }
            CountRing(p, items.size, 22.dp)
        }
    }

    @Composable
    override fun Minimal() {
        val s = single
        if (s != null) return s.Minimal()
        val p = overall
        if (p != null) CountRing(p, items.size, 24.dp) else DownloadGlyph(accent, 20.dp)
    }

    // ---------- Vue étendue ----------

    @Composable
    override fun Expanded(host: IslandHost) {
        val s = single
        if (s != null) return s.Expanded(host)
        val reduceMotion = rememberReduceMotion()
        Column(Modifier.fillMaxWidth()) {
            // En-tête : icône animée, « N téléchargements », progression globale.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { DownloadGlyph(accent, 22.dp) }
                Spacer(Modifier.width(12.dp))
                Text(
                    countLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                overall?.takeIf { !it.indeterminate }?.let { p ->
                    val overallLabel = stringResource(R.string.live_downloads_overall)
                    RollingText(
                        "${LiveFormat.percent(p.fraction)} %",
                        style = MaterialTheme.typography.titleSmall.merge(CompactDigits),
                        color = accent,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(18.dp).semantics { contentDescription = overallLabel }) {
                        ProgressRing(p, accent, size = 18.dp, stroke = 2.5.dp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Liste bornée à ~5 lignes, défilement au-delà. Les lignes sont indexées par clé de
            // notification : chaque barre garde son ressort quand l'ordre ou le nombre change.
            val maxHeight = ROW_HEIGHT * MAX_VISIBLE_ROWS + ROW_GAP * (MAX_VISIBLE_ROWS - 1)
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (reduceMotion) Modifier else Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)))
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
            ) {
                items.forEach { item ->
                    key(item.activity.key) { DownloadRow(item, host) }
                }
            }
        }
    }

    /** Toucher hors d'une ligne : ouvre l'appli du seul téléchargement, sinon replie l'île. */
    override fun onExpandedTap(host: IslandHost): Boolean = single?.onExpandedTap(host) ?: false

    // ---------- Éléments ----------

    /** Une ligne : icône de l'appli, nom du fichier, pourcentage, barre ; toucher = ouvrir. */
    @Composable
    private fun DownloadRow(item: LiveContent, host: IslandHost) {
        val a = item.activity
        val progress = a.progress
        val rowAccent = item.accent
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = a.contentIntent != null) {
                    if (NotificationIntents.send(host.context, a.contentIntent)) host.collapse()
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveAppGlyph(item.visuals, rowAccent, 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    a.title ?: a.text ?: item.visuals.appLabel.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                a.actions.take(2).forEach { action ->
                    CompactAction(action, rowAccent) { NotificationIntents.send(host.context, action.intent) }
                }
                if (progress != null && !progress.indeterminate) {
                    RollingText(
                        "${LiveFormat.percent(progress.fraction)} %",
                        style = MaterialTheme.typography.labelMedium.merge(CompactDigits),
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(4.dp))
                LiveProgressBar(progress, rowAccent)
            }
        }
    }

    /** Petit bouton d'action (Pause, Annuler…) avec léger enfoncement au ressort. */
    @Composable
    private fun CompactAction(action: LiveAction, color: Color, onClick: () -> Unit) {
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
        TextButton(
            onClick = onClick,
            interactionSource = source,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(containerColor = color.copy(alpha = 0.16f), contentColor = Color.White),
            modifier = Modifier
                .padding(start = 4.dp)
                .height(28.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            Text(action.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    /** Anneau de progression globale avec le nombre de téléchargements au centre. */
    @Composable
    private fun CountRing(progress: LiveProgress, count: Int, size: Dp) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            ProgressRing(progress, accent, size = size, stroke = 2.5.dp)
            val fontSize = with(LocalDensity.current) { (size * 0.45f).toSp() }
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall.merge(CompactDigits).copy(fontSize = fontSize, lineHeight = 10.sp),
                color = Color.White,
                maxLines = 1,
            )
        }
    }

    private companion object {
        val ROW_HEIGHT = 48.dp
        val ROW_GAP = 4.dp
        const val MAX_VISIBLE_ROWS = 5
    }
}

/**
 * Flèche de téléchargement qui descend doucement en boucle (valeur lue au dessin) ;
 * immobile si les animations sont réduites.
 */
@Composable
internal fun DownloadGlyph(color: Color, size: Dp) {
    val reduceMotion = rememberReduceMotion()
    val bob = if (!reduceMotion) {
        val t = rememberInfiniteTransition(label = "download")
        t.animateFloat(-1f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
    } else {
        null
    }
    Icon(
        Icons.Rounded.Download,
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(size)
            .graphicsLayer { translationY = (bob?.value ?: 0f) * size.toPx() * 0.08f },
    )
}
