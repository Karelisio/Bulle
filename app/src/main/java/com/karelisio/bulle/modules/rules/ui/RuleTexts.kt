package com.karelisio.bulle.modules.rules.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.ModuleRegistry
import com.karelisio.bulle.core.settings.MotionStyle
import com.karelisio.bulle.modules.rules.Rule
import com.karelisio.bulle.modules.rules.RuleAction
import com.karelisio.bulle.modules.rules.RuleCondition
import com.karelisio.bulle.modules.rules.RuleMatch
import com.karelisio.bulle.modules.rules.RuleTrigger
import com.karelisio.bulle.ui.settings.label
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Phrase lisible d'une règle : « Quand A et B, X, Y ». */
@Composable
fun Rule.sentence(): String {
    val conds = if (conditions.isEmpty()) {
        stringResource(R.string.rule_when_never)
    } else {
        val sep = stringResource(if (match == RuleMatch.ALL) R.string.rule_and else R.string.rule_or)
        conditions.map { it.text() }.joinToString(sep)
    }
    val acts = if (actions.isEmpty()) {
        stringResource(R.string.rule_do_nothing)
    } else {
        actions.map { it.text() }.joinToString(stringResource(R.string.rule_then))
    }
    return stringResource(R.string.rule_sentence, conds, acts)
}

@Composable
fun RuleCondition.text(): String = if (negated) stringResource(R.string.rule_not, trigger.text()) else trigger.text()

@Composable
fun RuleTrigger.text(): String = when (this) {
    is RuleTrigger.AppOpened -> stringResource(R.string.rule_when_app, label)
    RuleTrigger.HeadsetConnected -> stringResource(R.string.rule_when_headset)
    RuleTrigger.Charging -> stringResource(R.string.rule_when_charging)
    is RuleTrigger.TimeRange -> stringResource(R.string.rule_when_time, hhmm(startMinute), hhmm(endMinute))
    is RuleTrigger.WifiConnected -> if (ssid.isNullOrBlank()) {
        stringResource(R.string.rule_when_wifi_any)
    } else {
        stringResource(R.string.rule_when_wifi, ssid)
    }
    is RuleTrigger.BatteryLevel -> stringResource(
        if (below) R.string.rule_when_battery_below else R.string.rule_when_battery_above, threshold,
    )
    is RuleTrigger.BluetoothDevice -> stringResource(R.string.rule_when_bt, name.ifBlank { address })
    RuleTrigger.DarkMode -> stringResource(R.string.rule_when_dark)
    is RuleTrigger.Place -> if (label.isBlank()) {
        stringResource(R.string.rule_when_place_unnamed)
    } else {
        stringResource(R.string.rule_when_place, label)
    }
    RuleTrigger.ScreenLocked -> stringResource(R.string.rule_when_locked)
    RuleTrigger.InCall -> stringResource(R.string.rule_when_call)
    is RuleTrigger.DaysOfWeek -> stringResource(R.string.rule_when_days, daysText(days))
}

@Composable
fun daysText(days: Set<Int>): String = when (days) {
    emptySet<Int>() -> stringResource(R.string.rule_days_none)
    setOf(1, 2, 3, 4, 5) -> stringResource(R.string.rule_days_week)
    setOf(6, 7) -> stringResource(R.string.rule_days_weekend)
    (1..7).toSet() -> stringResource(R.string.rule_days_all)
    else -> days.sorted().joinToString(", ") { dayShort(it) }
}

/** Nom court d'un jour ISO (1 = lundi), dans la langue de l'appareil. */
fun dayShort(isoDay: Int): String = DayOfWeek.of(isoDay.coerceIn(1, 7)).getDisplayName(TextStyle.SHORT, Locale.getDefault())

@Composable
fun RuleAction.text(): String = when (this) {
    RuleAction.ShowIsland -> stringResource(R.string.rule_do_show)
    RuleAction.HideIsland -> stringResource(R.string.rule_do_hide)
    is RuleAction.SetMotionStyle -> stringResource(R.string.rule_do_style, stringResource(style.labelRes()))
    is RuleAction.Run -> stringResource(R.string.rule_do_run, action.label().replaceFirstChar { it.lowercase() })
    is RuleAction.SetModule -> stringResource(
        if (enabled) R.string.rule_do_module_on else R.string.rule_do_module_off, moduleTitle(moduleId),
    )
    RuleAction.FocusMode -> stringResource(R.string.rule_do_focus)
}

@Composable
fun moduleTitle(id: String): String = ModuleRegistry[id]?.let { stringResource(it.titleRes) } ?: id

fun MotionStyle.labelRes(): Int = when (this) {
    MotionStyle.SOFT -> R.string.motion_soft
    MotionStyle.SNAPPY -> R.string.motion_snappy
    MotionStyle.SOBER -> R.string.motion_sober
    MotionStyle.PLAYFUL -> R.string.motion_playful
}

internal fun hhmm(m: Int) = "%d:%02d".format(m / 60, m % 60)
