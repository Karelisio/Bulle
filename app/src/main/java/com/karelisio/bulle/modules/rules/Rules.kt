package com.karelisio.bulle.modules.rules

import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.settings.MotionStyle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** Déclencheur d'une règle. Les déclencheurs « d'état » restent vrais tant que la condition tient. */
@Serializable
sealed interface RuleTrigger {
    @Serializable
    @SerialName("app")
    data class AppOpened(val packageName: String, val label: String) : RuleTrigger

    @Serializable
    @SerialName("headset")
    data object HeadsetConnected : RuleTrigger

    @Serializable
    @SerialName("charging")
    data object Charging : RuleTrigger

    /** Créneau horaire [start, end[ en minutes depuis minuit (peut passer minuit). */
    @Serializable
    @SerialName("time")
    data class TimeRange(val startMinute: Int, val endMinute: Int) : RuleTrigger

    /** Wi-Fi connecté (n'importe lequel si [ssid] est null). */
    @Serializable
    @SerialName("wifi")
    data class WifiConnected(val ssid: String? = null) : RuleTrigger
}

/** Action d'une règle. */
@Serializable
sealed interface RuleAction {
    @Serializable
    @SerialName("show")
    data object ShowIsland : RuleAction

    @Serializable
    @SerialName("hide")
    data object HideIsland : RuleAction

    /** Style d'animation appliqué tant que la règle est active. */
    @Serializable
    @SerialName("style")
    data class SetMotionStyle(val style: MotionStyle) : RuleAction

    /** Action de geste ou raccourci, lancée quand la règle devient active. */
    @Serializable
    @SerialName("run")
    data class Run(val action: GestureAction) : RuleAction
}

@Serializable
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val trigger: RuleTrigger,
    val action: RuleAction,
)

@Serializable
data class RulesSettings(val rules: List<Rule> = emptyList())

/** Ce que les règles actives imposent à l'île en ce moment. */
data class RuleEffects(
    /** true = forcer l'affichage, false = masquer, null = rien. */
    val visibility: Boolean? = null,
    val motionStyle: MotionStyle? = null,
)

/** État de l'environnement observé par le moteur de règles. */
data class RuleWorld(
    val foregroundPackage: String? = null,
    val headset: Boolean = false,
    val charging: Boolean = false,
    val minuteOfDay: Int = 0,
    val wifiSsid: String? = null,
    val wifiConnected: Boolean = false,
)

object RuleLogic {
    fun isActive(trigger: RuleTrigger, w: RuleWorld): Boolean = when (trigger) {
        is RuleTrigger.AppOpened -> w.foregroundPackage == trigger.packageName
        RuleTrigger.HeadsetConnected -> w.headset
        RuleTrigger.Charging -> w.charging
        is RuleTrigger.TimeRange -> inRange(w.minuteOfDay, trigger.startMinute, trigger.endMinute)
        is RuleTrigger.WifiConnected -> w.wifiConnected &&
            (trigger.ssid == null || trigger.ssid.equals(w.wifiSsid, ignoreCase = false))
    }

    fun inRange(m: Int, start: Int, end: Int): Boolean =
        if (start <= end) m in start until end else m >= start || m < end

    /** Effets cumulés : la dernière règle active de la liste l'emporte. */
    fun effects(rules: List<Rule>, w: RuleWorld): RuleEffects {
        var e = RuleEffects()
        for (r in rules) {
            if (!r.enabled || !isActive(r.trigger, w)) continue
            e = when (val a = r.action) {
                RuleAction.ShowIsland -> e.copy(visibility = true)
                RuleAction.HideIsland -> e.copy(visibility = false)
                is RuleAction.SetMotionStyle -> e.copy(motionStyle = a.style)
                is RuleAction.Run -> e
            }
        }
        return e
    }
}
