package com.karelisio.bulle.modules.external

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.alerts.sources.sendFromIsland
import com.karelisio.bulle.modules.external.ui.ExternalExpanded
import com.karelisio.bulle.modules.external.ui.ExternalLeading
import com.karelisio.bulle.modules.external.ui.ExternalTrailing

/** Contenu de l'île construit à partir d'un contenu reçu d'une appli ([ExternalSpec]). */
data class ExternalContent(val spec: ExternalSpec) : IslandContent() {
    override val key: String = "external:${spec.key}"
    override val moduleId: String = ExternalModule.ID
    override val priority: Priority = when (spec.priority) {
        ExternalPriority.MEDIA -> Priority.MEDIA
        ExternalPriority.LIVE -> Priority.LIVE
        ExternalPriority.ALERT -> Priority.ALERT
    }
    override val postedAt: Long get() = spec.receivedAt
    override val timeoutMs: Long? get() = spec.timeoutMs
    override val autoExpandMs: Long? get() = if (spec.autoExpand) ExternalApi.AUTO_EXPAND_MS else null
    override val accent: Color get() = accentOf(spec)
    override val compactSlotWidth: Dp get() = slotWidthOf(spec.template)
    override val showWhenHidden: Boolean get() = spec.showWhenHidden
    override val canBeSecondary: Boolean get() = spec.template != ExternalTemplate.BREATHING
    override val compactSubtitle: String? get() = spec.subtitle ?: spec.text

    @Composable
    override fun Leading() = ExternalLeading(spec, accent)

    @Composable
    override fun Trailing() = ExternalTrailing(spec, accent)

    @Composable
    override fun Expanded(host: IslandHost) = ExternalExpanded(spec, accent, host, onDismiss = { dismiss(host) })

    /** Toucher : ouvre l'appli (contentIntent) ; une alerte disparaît une fois ouverte. */
    override fun onExpandedTap(host: IslandHost): Boolean {
        val pi = spec.contentIntent ?: return false
        pi.sendFromIsland()
        if (spec.priority == ExternalPriority.ALERT) dismiss(host)
        return true
    }

    private fun dismiss(host: IslandHost) {
        ExternalModule.dismiss(spec.key)
        host.dismiss(key)
    }

    companion object {
        val StatusOk = Color(0xFF5BD68A)
        val StatusWarn = Color(0xFFFFC14D)
        val StatusDown = Color(0xFFFF5A5F)
        val HeartPink = Color(0xFFFF6FA5)
        val BreathTeal = Color(0xFF7FD8E6)
        val DefaultAccent = Color(0xFF8AB4FF)

        fun statusColor(status: ExternalStatus?): Color = when (status) {
            ExternalStatus.WARN -> StatusWarn
            ExternalStatus.DOWN -> StatusDown
            else -> StatusOk
        }

        fun accentOf(spec: ExternalSpec): Color = spec.accent?.let { Color(it) } ?: when (spec.template) {
            ExternalTemplate.HEART -> HeartPink
            ExternalTemplate.BREATHING -> BreathTeal
            ExternalTemplate.STATUS -> statusColor(spec.status)
            else -> DefaultAccent
        }

        fun slotWidthOf(template: ExternalTemplate): Dp = when (template) {
            ExternalTemplate.STANDARD, ExternalTemplate.HEART -> 84.dp
            ExternalTemplate.COUNTDOWN, ExternalTemplate.CHRONOMETER, ExternalTemplate.STATUS -> 72.dp
            ExternalTemplate.BREATHING -> 68.dp
            ExternalTemplate.PROGRESS, ExternalTemplate.LIST -> 52.dp
        }
    }
}
