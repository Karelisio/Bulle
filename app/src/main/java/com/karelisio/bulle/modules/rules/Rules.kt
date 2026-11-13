package com.karelisio.bulle.modules.rules

import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.MotionStyle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
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

    /** Niveau de batterie ≤ [threshold] % (si [below]) ou ≥ [threshold] %. */
    @Serializable
    @SerialName("battery")
    data class BatteryLevel(val threshold: Int = 20, val below: Boolean = true) : RuleTrigger

    /** Appareil Bluetooth appairé précis connecté (adresse matérielle). */
    @Serializable
    @SerialName("bt_device")
    data class BluetoothDevice(val address: String, val name: String = "") : RuleTrigger

    /** Thème sombre du système actif. */
    @Serializable
    @SerialName("dark")
    data object DarkMode : RuleTrigger

    /**
     * Lieu : cercle (centre + rayon). [id] stable pour l'hystérésis d'entrée/sortie
     * (l'état « dedans » est suivi par le moteur, pas recalculé à chaque position).
     */
    @Serializable
    @SerialName("place")
    data class Place(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Int = 200,
        val label: String = "",
        val id: String = UUID.randomUUID().toString(),
    ) : RuleTrigger {
        companion object {
            const val MIN_RADIUS_M = 50
            const val MAX_RADIUS_M = 5_000
        }
    }

    /** Écran verrouillé (ou éteint). « Déverrouillé » = cette condition avec la négation. */
    @Serializable
    @SerialName("locked")
    data object ScreenLocked : RuleTrigger

    /** Appel en cours (téléphone ou appel audio/vidéo d'une appli). */
    @Serializable
    @SerialName("in_call")
    data object InCall : RuleTrigger

    /** Jours de la semaine, ISO : 1 = lundi … 7 = dimanche. */
    @Serializable
    @SerialName("days")
    data class DaysOfWeek(val days: Set<Int> = setOf(1, 2, 3, 4, 5)) : RuleTrigger
}

/** Une condition = un déclencheur, éventuellement inversé (« pas »). */
@Serializable
data class RuleCondition(val trigger: RuleTrigger, val negated: Boolean = false)

/** Combinaison des conditions d'une règle. */
@Serializable
enum class RuleMatch { ALL, ANY }

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

    /** Active ou désactive un module tant que la règle est active. */
    @Serializable
    @SerialName("module")
    data class SetModule(val moduleId: String, val enabled: Boolean = false) : RuleAction

    /** Mode focus tant que la règle est active. */
    @Serializable
    @SerialName("focus")
    data object FocusMode : RuleAction
}

/**
 * Règle : une liste de conditions combinées par [match] et une liste d'actions.
 * Ancien format (une seule paire `trigger`/`action`) relu par [RuleSerializer].
 */
@Serializable
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val conditions: List<RuleCondition> = emptyList(),
    val match: RuleMatch = RuleMatch.ALL,
    val actions: List<RuleAction> = emptyList(),
) {
    /** Règle simple « quand [trigger], [action] » (ancien modèle). */
    constructor(
        trigger: RuleTrigger,
        action: RuleAction,
        id: String = UUID.randomUUID().toString(),
        enabled: Boolean = true,
    ) : this(id = id, enabled = enabled, conditions = listOf(RuleCondition(trigger)), actions = listOf(action))

    /** Premier déclencheur (compatibilité avec l'ancien modèle à déclencheur unique). Non sérialisé. */
    val trigger: RuleTrigger? get() = conditions.firstOrNull()?.trigger

    /** Remplace le premier déclencheur (compatibilité avec l'ancien modèle). */
    fun copy(trigger: RuleTrigger): Rule = copy(
        conditions = if (conditions.isEmpty()) {
            listOf(RuleCondition(trigger))
        } else {
            listOf(conditions.first().copy(trigger = trigger)) + conditions.drop(1)
        },
    )

    /** Valeurs ramenées dans des bornes valides (sauvegarde modifiée à la main, corrompue…). */
    fun sanitized(): Rule = copy(conditions = conditions.map { it.copy(trigger = it.trigger.sanitized()) })
}

/** Bornes des paramètres d'un déclencheur. */
fun RuleTrigger.sanitized(): RuleTrigger = when (this) {
    is RuleTrigger.TimeRange -> RuleTrigger.TimeRange(startMinute.coerceIn(0, 1439), endMinute.coerceIn(0, 1439))
    is RuleTrigger.BatteryLevel -> copy(threshold = threshold.coerceIn(1, 100))
    is RuleTrigger.Place -> copy(
        latitude = latitude.coerceIn(-90.0, 90.0),
        longitude = longitude.coerceIn(-180.0, 180.0),
        radiusMeters = radiusMeters.coerceIn(RuleTrigger.Place.MIN_RADIUS_M, RuleTrigger.Place.MAX_RADIUS_M),
    )
    is RuleTrigger.DaysOfWeek -> copy(days = days.filterTo(HashSet()) { it in 1..7 })
    else -> this
}

/**
 * Lecture compatible des règles : `{trigger, action}` (versions ≤ 0.6) devient
 * `{conditions: [{trigger}], actions: [action]}`. L'écriture se fait toujours au nouveau format.
 */
object RuleSerializer : JsonTransformingSerializer<Rule>(Rule.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element as? JsonObject ?: return element
        val legacyTrigger = obj["trigger"]
        val legacyAction = obj["action"]
        val hasConditions = (obj["conditions"] as? JsonArray)?.isNotEmpty() == true
        val hasActions = (obj["actions"] as? JsonArray)?.isNotEmpty() == true
        if ((legacyTrigger == null || hasConditions) && (legacyAction == null || hasActions)) return obj
        return buildJsonObject {
            obj.forEach { (k, v) -> if (k != "trigger" && k != "action") put(k, v) }
            if (legacyTrigger is JsonObject && !hasConditions) {
                put("conditions", JsonArray(listOf(buildJsonObject { put("trigger", legacyTrigger) })))
            }
            if (legacyAction is JsonObject && !hasActions) put("actions", JsonArray(listOf(legacyAction)))
        }
    }
}

@Serializable
data class RulesSettings(val rules: List<@Serializable(with = RuleSerializer::class) Rule> = emptyList())

/** Ce que les règles actives imposent à l'île en ce moment. */
data class RuleEffects(
    /** true = forcer l'affichage, false = masquer, null = rien. */
    val visibility: Boolean? = null,
    val motionStyle: MotionStyle? = null,
    /** Activation forcée de modules (id → activé) tant que les règles sont actives. */
    val modules: Map<String, Boolean> = emptyMap(),
    /** Une règle active demande le mode focus. */
    val focus: Boolean = false,
) {
    /** Réglages effectifs : réglages enregistrés + effets des règles (style, modules). */
    fun applyTo(s: BulleSettings): BulleSettings {
        var out = s
        motionStyle?.let { out = out.copy(motion = out.motion.copy(style = it)) }
        if (modules.isNotEmpty()) out = out.copy(modules = out.modules + modules)
        return out
    }
}

/** État de l'environnement observé par le moteur de règles. */
data class RuleWorld(
    val foregroundPackage: String? = null,
    val headset: Boolean = false,
    val charging: Boolean = false,
    val minuteOfDay: Int = 0,
    val wifiSsid: String? = null,
    val wifiConnected: Boolean = false,
    /** Niveau de batterie en %, -1 = inconnu. */
    val batteryLevel: Int = -1,
    /** Adresses des appareils Bluetooth connectés. */
    val bluetoothDevices: Set<String> = emptySet(),
    val darkMode: Boolean = false,
    /** Identifiants des lieux où l'on se trouve (avec hystérésis, voir [Geo]). */
    val insidePlaces: Set<String> = emptySet(),
    val screenLocked: Boolean = false,
    val inCall: Boolean = false,
    /** Jour ISO : 1 = lundi … 7 = dimanche. */
    val dayOfWeek: Int = 1,
)

object RuleLogic {
    fun isActive(trigger: RuleTrigger, w: RuleWorld): Boolean = when (trigger) {
        is RuleTrigger.AppOpened -> w.foregroundPackage == trigger.packageName
        RuleTrigger.HeadsetConnected -> w.headset
        RuleTrigger.Charging -> w.charging
        is RuleTrigger.TimeRange -> inRange(w.minuteOfDay, trigger.startMinute, trigger.endMinute)
        is RuleTrigger.WifiConnected -> w.wifiConnected &&
            (trigger.ssid == null || trigger.ssid.equals(w.wifiSsid, ignoreCase = false))
        is RuleTrigger.BatteryLevel -> w.batteryLevel in 0..100 &&
            if (trigger.below) w.batteryLevel <= trigger.threshold else w.batteryLevel >= trigger.threshold
        is RuleTrigger.BluetoothDevice -> w.bluetoothDevices.any { it.equals(trigger.address, ignoreCase = true) }
        RuleTrigger.DarkMode -> w.darkMode
        is RuleTrigger.Place -> trigger.id in w.insidePlaces
        RuleTrigger.ScreenLocked -> w.screenLocked
        RuleTrigger.InCall -> w.inCall
        is RuleTrigger.DaysOfWeek -> w.dayOfWeek in trigger.days
    }

    fun isActive(condition: RuleCondition, w: RuleWorld): Boolean = isActive(condition.trigger, w) != condition.negated

    /** Règle active : ET (toutes) ou OU (au moins une) de ses conditions. Sans condition : jamais. */
    fun isActive(rule: Rule, w: RuleWorld): Boolean {
        if (rule.conditions.isEmpty()) return false
        return when (rule.match) {
            RuleMatch.ALL -> rule.conditions.all { isActive(it, w) }
            RuleMatch.ANY -> rule.conditions.any { isActive(it, w) }
        }
    }

    fun inRange(m: Int, start: Int, end: Int): Boolean =
        if (start <= end) m in start until end else m >= start || m < end

    /** Jour ISO (1 = lundi) depuis `Calendar.DAY_OF_WEEK` (1 = dimanche). */
    fun isoDay(calendarDay: Int): Int = (calendarDay + 5) % 7 + 1

    /** Effets cumulés : la dernière règle active de la liste l'emporte. */
    fun effects(rules: List<Rule>, w: RuleWorld): RuleEffects {
        var e = RuleEffects()
        for (r in rules) {
            if (!r.enabled || !isActive(r, w)) continue
            for (a in r.actions) {
                e = when (a) {
                    RuleAction.ShowIsland -> e.copy(visibility = true)
                    RuleAction.HideIsland -> e.copy(visibility = false)
                    is RuleAction.SetMotionStyle -> e.copy(motionStyle = a.style)
                    is RuleAction.Run -> e
                    is RuleAction.SetModule -> e.copy(modules = e.modules + (a.moduleId to a.enabled))
                    RuleAction.FocusMode -> e.copy(focus = true)
                }
            }
        }
        return e
    }

    /** Actions « Lancer » des règles qui viennent de s'activer (front montant). */
    fun risingRuns(rules: List<Rule>, previouslyActive: Set<String>, w: RuleWorld): Pair<Set<String>, List<GestureAction>> {
        val nowActive = rules.filter { it.enabled && isActive(it, w) }.mapTo(LinkedHashSet()) { it.id }
        val runs = rules.filter { it.id in nowActive && it.id !in previouslyActive }
            .flatMap { r -> r.actions.filterIsInstance<RuleAction.Run>().map { it.action } }
        return nowActive to runs
    }

    /** Tous les lieux utilisés par des règles actives (activées). */
    fun places(rules: List<Rule>): List<RuleTrigger.Place> =
        rules.filter { it.enabled }.flatMap { r -> r.conditions.mapNotNull { it.trigger as? RuleTrigger.Place } }

    fun usesBluetooth(rules: List<Rule>): Boolean =
        rules.any { r -> r.enabled && r.conditions.any { it.trigger is RuleTrigger.BluetoothDevice } }
}
