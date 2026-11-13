package com.karelisio.bulle.modules.external

import android.app.PendingIntent
import android.graphics.drawable.Icon
import com.karelisio.bulle.modules.external.ExternalApi as Api

/** Une ligne du modèle `list`. */
data class ExternalItem(val text: String, val checked: Boolean = false)

/** Un bouton fourni par l'appli : libellé + PendingIntent (null seulement pour les exemples locaux). */
data class ExternalAction(val label: String, val intent: PendingIntent?)

/** Préréglage d'affichage d'une appli connue (écosystème Karelisio) quand elle n'envoie rien de spécial. */
data class ExternalPreset(
    val template: ExternalTemplate = ExternalTemplate.STANDARD,
    /** Couleur ARGB. */
    val accent: Int? = null,
    val priority: ExternalPriority? = null,
)

/**
 * Contenu reçu d'une appli, validé et borné. Sa clé est « paquet appelant / id » :
 * une appli ne peut ni lire ni remplacer les contenus d'une autre.
 */
data class ExternalSpec(
    val packageName: String,
    val id: String,
    val template: ExternalTemplate = ExternalTemplate.STANDARD,
    val title: String? = null,
    val text: String? = null,
    val subtitle: String? = null,
    val icon: Icon? = null,
    val iconRes: Int? = null,
    /** Couleur ARGB opaque. */
    val accent: Int? = null,
    val priority: ExternalPriority = ExternalPriority.LIVE,
    val timeoutMs: Long? = null,
    val autoExpand: Boolean = false,
    val progress: Int = 0,
    val max: Int = 100,
    val indeterminate: Boolean = false,
    val targetTime: Long? = null,
    val startTime: Long? = null,
    val items: List<ExternalItem> = emptyList(),
    val status: ExternalStatus? = null,
    val actions: List<ExternalAction> = emptyList(),
    val contentIntent: PendingIntent? = null,
    val showWhenHidden: Boolean = false,
    val breathPattern: BreathPattern = BreathPattern.RELAX_478,
    /** Réception (elapsedRealtime) : base de l'expiration. */
    val receivedAt: Long = 0L,
    /** Exemple poussé localement (bouton « Tester ») : aucune appli derrière. */
    val local: Boolean = false,
) {
    val key: String get() = keyOf(packageName, id)

    /** Fin de vie (elapsedRealtime) ou null si aucune. */
    val expiresAt: Long? get() = timeoutMs?.let { receivedAt + it }

    companion object {
        fun keyOf(packageName: String, id: String) = "$packageName/$id"
    }
}

/** Résultat de l'analyse d'un broadcast. */
sealed interface ExternalCommand {
    data class Show(val spec: ExternalSpec) : ExternalCommand
    data class Dismiss(val packageName: String, val id: String) : ExternalCommand {
        val key: String get() = ExternalSpec.keyOf(packageName, id)
    }
    data class Invalid(val reason: String) : ExternalCommand
}

/** Accès aux extras d'un Intent (abstrait pour tester l'analyse en JUnit pur). */
fun interface ExternalExtras {
    operator fun get(key: String): Any?
}

/**
 * Analyse pure d'un broadcast de l'API d'intégration : chaque extra est validé et borné,
 * une valeur invalide est remplacée par la valeur par défaut. Seuls l'action et l'id sont obligatoires.
 */
object ExternalParser {
    private const val DAY = 24 * 60 * 60 * 1000L
    private val ID_REGEX = Regex("[A-Za-z0-9._:\\-]{1,${Api.MAX_ID_LENGTH}}")

    /**
     * @param caller paquet de l'appli appelante, déjà vérifié (voir [ExternalReceiver]).
     * @param nowWall heure murale (System.currentTimeMillis) pour borner targetTime / startTime.
     * @param nowElapsed elapsedRealtime : instant de réception.
     * @param iconCheck valide une Icon reçue (types autorisés, ressource de l'appelant).
     */
    fun parse(
        action: String?,
        extras: ExternalExtras,
        caller: String,
        nowWall: Long,
        nowElapsed: Long,
        preset: ExternalPreset? = null,
        iconCheck: (Icon, String) -> Icon? = ExternalIcons::sanitize,
    ): ExternalCommand {
        val id = (extras[Api.EXTRA_ID] as? CharSequence)?.toString()?.trim()
            ?: return ExternalCommand.Invalid("id manquant")
        if (!ID_REGEX.matches(id)) return ExternalCommand.Invalid("id invalide")

        return when (action) {
            Api.ACTION_DISMISS -> ExternalCommand.Dismiss(caller, id)
            Api.ACTION_SHOW, Api.ACTION_UPDATE -> ExternalCommand.Show(
                show(id, extras, caller, nowWall, nowElapsed, preset, iconCheck),
            )
            else -> ExternalCommand.Invalid("action inconnue")
        }
    }

    private fun show(
        id: String,
        x: ExternalExtras,
        caller: String,
        nowWall: Long,
        nowElapsed: Long,
        preset: ExternalPreset?,
        iconCheck: (Icon, String) -> Icon?,
    ): ExternalSpec {
        var template = ExternalTemplate.parse(x.string(Api.EXTRA_TEMPLATE)) ?: preset?.template ?: ExternalTemplate.STANDARD

        // Heures murales bornées : compte à rebours sur un peu plus d'un an, chronomètre sur 20 ans.
        val target = x.long(Api.EXTRA_TARGET_TIME)?.takeIf { it in (nowWall - DAY)..(nowWall + 400 * DAY) }
        val start = x.long(Api.EXTRA_START_TIME)?.takeIf { it in (nowWall - 20 * 365 * DAY)..(nowWall + 60_000L) }
            ?.takeIf { target == null || it <= target }

        val items = parseItems(x)

        // Modèle impossible à rendre sans sa donnée : repli sur le modèle standard.
        template = when {
            template == ExternalTemplate.COUNTDOWN && target == null -> ExternalTemplate.STANDARD
            template == ExternalTemplate.CHRONOMETER && start == null -> ExternalTemplate.STANDARD
            template == ExternalTemplate.LIST && items.isEmpty() -> ExternalTemplate.STANDARD
            else -> template
        }

        val status = if (template == ExternalTemplate.STATUS) {
            ExternalStatus.parse(x.string(Api.EXTRA_STATUS)) ?: ExternalStatus.OK
        } else {
            null
        }

        val requested = ExternalPriority.parse(x.string(Api.EXTRA_PRIORITY)) ?: preset?.priority
        val priority = when {
            // Un service en panne est toujours une alerte.
            status == ExternalStatus.DOWN -> ExternalPriority.ALERT
            requested != null -> requested
            template == ExternalTemplate.HEART -> ExternalPriority.ALERT
            else -> ExternalPriority.LIVE
        }

        val timeout = x.long(Api.EXTRA_TIMEOUT_MS)?.coerceIn(Api.MIN_TIMEOUT_MS, Api.MAX_TIMEOUT_MS)
            ?: when {
                priority == ExternalPriority.ALERT -> Api.DEFAULT_ALERT_TIMEOUT_MS
                // Un compte à rebours disparaît une minute après l'échéance.
                template == ExternalTemplate.COUNTDOWN && target != null ->
                    (target - nowWall + 60_000L).coerceIn(Api.MIN_TIMEOUT_MS, Api.MAX_TIMEOUT_MS)
                else -> Api.MAX_TIMEOUT_MS
            }

        val max = (x.long(Api.EXTRA_MAX) ?: 100L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        val progress = (x.long(Api.EXTRA_PROGRESS) ?: 0L).coerceIn(0L, max.toLong()).toInt()

        return ExternalSpec(
            packageName = caller,
            id = id,
            template = template,
            title = x.text(Api.EXTRA_TITLE, Api.MAX_TITLE_LENGTH),
            text = x.text(Api.EXTRA_TEXT, Api.MAX_TEXT_LENGTH),
            subtitle = x.text(Api.EXTRA_SUBTITLE, Api.MAX_SUBTITLE_LENGTH),
            icon = (x[Api.EXTRA_ICON] as? Icon)?.let { iconCheck(it, caller) },
            iconRes = x.long(Api.EXTRA_ICON_RES)?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt(),
            accent = parseColor(x[Api.EXTRA_ACCENT_COLOR]) ?: preset?.accent,
            priority = priority,
            timeoutMs = timeout,
            autoExpand = x.bool(Api.EXTRA_AUTO_EXPAND) ?: (priority == ExternalPriority.ALERT),
            progress = progress,
            max = max,
            indeterminate = x.bool(Api.EXTRA_INDETERMINATE) ?: false,
            targetTime = target,
            startTime = start,
            items = items,
            status = status,
            actions = parseActions(x),
            contentIntent = x[Api.EXTRA_CONTENT_INTENT] as? PendingIntent,
            // Visible malgré le masquage (plein écran…) : réservé aux alertes.
            showWhenHidden = (x.bool(Api.EXTRA_SHOW_WHEN_HIDDEN) ?: false) && priority == ExternalPriority.ALERT,
            breathPattern = BreathPattern.parse(x.string(Api.EXTRA_BREATH_PATTERN)) ?: BreathPattern.RELAX_478,
            receivedAt = nowElapsed,
        )
    }

    private fun parseItems(x: ExternalExtras): List<ExternalItem> {
        // Les coches sont associées par position dans la liste ENVOYÉE (avant retrait des lignes vides).
        val checked = (x[Api.EXTRA_ITEMS_CHECKED] as? BooleanArray) ?: BooleanArray(0)
        return x.stringList(Api.EXTRA_ITEMS)
            .mapIndexedNotNull { i, t -> clean(t, Api.MAX_ITEM_LENGTH)?.let { ExternalItem(it, checked.getOrElse(i) { false }) } }
            .take(Api.MAX_ITEMS)
    }

    private fun parseActions(x: ExternalExtras): List<ExternalAction> {
        val labels = x.stringList(Api.EXTRA_ACTION_LABELS)
        val intents = when (val v = x[Api.EXTRA_ACTION_INTENTS]) {
            is Array<*> -> v.toList()
            is List<*> -> v
            else -> emptyList()
        }
        return labels.indices.mapNotNull { i ->
            val label = clean(labels[i], Api.MAX_ACTION_LABEL_LENGTH) ?: return@mapNotNull null
            val pi = intents.getOrNull(i) as? PendingIntent ?: return@mapNotNull null
            ExternalAction(label, pi)
        }.take(Api.MAX_ACTIONS)
    }

    /** Couleur ARGB depuis un Int/Long (alpha nul = opaque) ou une chaîne « #RRGGBB » / « #AARRGGBB ». */
    fun parseColor(v: Any?): Int? {
        val raw: Long = when (v) {
            is Int -> v.toLong() and 0xFFFFFFFFL
            is Long -> v and 0xFFFFFFFFL
            is CharSequence -> {
                val s = v.toString().trim().removePrefix("#")
                if (s.length != 6 && s.length != 8) return null
                s.toLongOrNull(16) ?: return null
            }
            else -> return null
        }
        val argb = if (raw ushr 24 == 0L) raw or 0xFF000000L else raw
        return argb.toInt()
    }

    /** Texte nettoyé : sans caractères de contrôle, espaces normalisés, tronqué à [max] (avec « … »). */
    fun clean(v: CharSequence?, max: Int): String? {
        val s = v?.toString()
            ?.replace(Regex("[\\p{Cntrl}\\p{Zl}\\p{Zp}]+"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"
    }

    private fun ExternalExtras.string(key: String): String? = (this[key] as? CharSequence)?.toString()
    private fun ExternalExtras.text(key: String, max: Int): String? = clean(this[key] as? CharSequence, max)
    private fun ExternalExtras.long(key: String): Long? = when (val v = this[key]) {
        is Long -> v
        is Int -> v.toLong()
        is Short -> v.toLong()
        is Byte -> v.toLong()
        is Double -> if (v.isFinite()) v.toLong() else null
        is Float -> if (v.isFinite()) v.toLong() else null
        is CharSequence -> v.toString().trim().toLongOrNull()
        else -> null
    }
    private fun ExternalExtras.bool(key: String): Boolean? = when (val v = this[key]) {
        is Boolean -> v
        is CharSequence -> v.toString().trim().lowercase().toBooleanStrictOrNull()
        is Int -> v != 0
        else -> null
    }
    private fun ExternalExtras.stringList(key: String): List<CharSequence?> = when (val v = this[key]) {
        is Array<*> -> v.take(32).map { it as? CharSequence }
        is List<*> -> v.take(32).map { it as? CharSequence }
        else -> emptyList()
    }
}
