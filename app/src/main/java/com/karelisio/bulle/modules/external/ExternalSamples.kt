package com.karelisio.bulle.modules.external

import android.content.Context
import com.karelisio.bulle.R

/**
 * Exemples locaux de chaque modèle (bouton « Tester » de l'écran Intégrations, tests de rendu).
 * Textes de démonstration traduits ; aucune appli n'est contactée.
 */
object ExternalSamples {
    /** Durée d'affichage de chaque exemple pendant la démonstration enchaînée. */
    const val DEMO_STEP_MS = 4_500L

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun of(context: Context, template: ExternalTemplate, nowWall: Long, packageName: String = context.packageName): ExternalSpec {
        val s: (Int) -> String = context::getString
        val base = ExternalSpec(packageName = packageName, id = "demo-${template.wire}", template = template, local = true)
        return when (template) {
            ExternalTemplate.STANDARD -> base.copy(
                title = s(R.string.ext_demo_standard_title),
                text = s(R.string.ext_demo_standard_text),
                subtitle = s(R.string.eco_miroir),
                accent = 0xFFC7A6FF.toInt(),
            )
            ExternalTemplate.PROGRESS -> base.copy(
                title = s(R.string.ext_demo_progress_title),
                text = s(R.string.ext_demo_progress_text),
                progress = 65, max = 100,
                accent = 0xFF9C8CFF.toInt(),
            )
            ExternalTemplate.COUNTDOWN -> base.copy(
                title = s(R.string.ext_demo_countdown_title),
                text = s(R.string.ext_demo_countdown_text),
                startTime = nowWall - 2 * HOUR,
                targetTime = nowWall + 42 * MINUTE,
                accent = 0xFF6FB6FF.toInt(),
            )
            ExternalTemplate.CHRONOMETER -> base.copy(
                title = s(R.string.ext_demo_chrono_title),
                text = s(R.string.ext_demo_chrono_text),
                startTime = nowWall - (12 * DAY + 5 * HOUR),
                accent = 0xFF5BD68A.toInt(),
            )
            ExternalTemplate.LIST -> base.copy(
                title = s(R.string.ext_demo_list_title),
                items = listOf(
                    ExternalItem(s(R.string.ext_demo_list_1), checked = true),
                    ExternalItem(s(R.string.ext_demo_list_2)),
                    ExternalItem(s(R.string.ext_demo_list_3)),
                    ExternalItem(s(R.string.ext_demo_list_4), checked = true),
                    ExternalItem(s(R.string.ext_demo_list_5)),
                ),
                accent = 0xFFFFA24D.toInt(),
            )
            ExternalTemplate.STATUS -> base.copy(
                title = "vps-paris",
                text = s(R.string.ext_demo_status_text),
                status = ExternalStatus.DOWN,
                priority = ExternalPriority.ALERT,
                accent = 0xFF4DD0C4.toInt(),
                actions = listOf(ExternalAction(s(R.string.ext_demo_status_action1), null), ExternalAction(s(R.string.ext_demo_status_action2), null)),
            )
            ExternalTemplate.HEART -> base.copy(
                title = s(R.string.ext_demo_heart_title),
                text = s(R.string.ext_demo_heart_text),
                priority = ExternalPriority.ALERT,
                accent = 0xFFFF6FA5.toInt(),
            )
            ExternalTemplate.BREATHING -> base.copy(
                title = s(R.string.ext_demo_breath_title),
                text = s(R.string.ext_demo_breath_text),
                breathPattern = BreathPattern.RELAX_478,
                accent = 0xFF7FD8E6.toInt(),
            )
        }
    }

    fun all(context: Context, nowWall: Long): List<ExternalSpec> =
        ExternalTemplate.entries.map { of(context, it, nowWall) }
}
