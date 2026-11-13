package com.karelisio.bulle.modules.external

/**
 * Contrat public de l'API d'intégration de Bulle (voir docs/INTEGRATION.md).
 *
 * Une appli autorisée (permission [PERMISSION_SHOW], accordée par signature ou certificat connu)
 * envoie un broadcast explicite vers Bulle avec l'une des actions ci-dessous et des extras.
 * Toutes les valeurs sont validées et bornées par [ExternalParser] : une valeur invalide est
 * ignorée (valeur par défaut), jamais fatale.
 *
 * NE PAS renommer ces constantes : elles sont copiées telles quelles dans les applis clientes.
 */
object ExternalApi {
    const val BULLE_PACKAGE = "com.karelisio.bulle"
    const val PERMISSION_SHOW = "com.karelisio.bulle.permission.SHOW"

    // Actions.
    const val ACTION_SHOW = "com.karelisio.bulle.action.SHOW"
    const val ACTION_UPDATE = "com.karelisio.bulle.action.UPDATE"
    const val ACTION_DISMISS = "com.karelisio.bulle.action.DISMISS"

    // Identité de l'appelant : PendingIntent créé par l'appli (son creatorPackage est sûr).
    const val EXTRA_SENDER = "sender"

    // Extras.
    const val EXTRA_ID = "id"
    const val EXTRA_TEMPLATE = "template"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_SUBTITLE = "subtitle"
    const val EXTRA_ICON = "icon"
    const val EXTRA_ICON_RES = "iconRes"
    const val EXTRA_ACCENT_COLOR = "accentColor"
    const val EXTRA_PRIORITY = "priority"
    const val EXTRA_TIMEOUT_MS = "timeoutMs"
    const val EXTRA_AUTO_EXPAND = "autoExpand"
    const val EXTRA_PROGRESS = "progress"
    const val EXTRA_MAX = "max"
    const val EXTRA_INDETERMINATE = "indeterminate"
    const val EXTRA_TARGET_TIME = "targetTime"
    const val EXTRA_START_TIME = "startTime"
    const val EXTRA_ITEMS = "items"
    const val EXTRA_ITEMS_CHECKED = "itemsChecked"
    const val EXTRA_STATUS = "status"
    const val EXTRA_ACTION_LABELS = "actionLabels"
    const val EXTRA_ACTION_INTENTS = "actionIntents"
    const val EXTRA_CONTENT_INTENT = "contentIntent"
    const val EXTRA_SHOW_WHEN_HIDDEN = "showWhenHidden"
    const val EXTRA_BREATH_PATTERN = "breathPattern"

    // Bornes.
    const val MAX_ID_LENGTH = 64
    const val MAX_TITLE_LENGTH = 80
    const val MAX_TEXT_LENGTH = 240
    const val MAX_SUBTITLE_LENGTH = 80
    const val MAX_ITEMS = 8
    const val MAX_ITEM_LENGTH = 80
    const val MAX_ACTIONS = 3
    const val MAX_ACTION_LABEL_LENGTH = 24
    const val MIN_TIMEOUT_MS = 1_000L
    const val MAX_TIMEOUT_MS = 12 * 60 * 60 * 1000L

    /** Durée de vie par défaut d'une alerte (priorité ALERT) sans timeoutMs. */
    const val DEFAULT_ALERT_TIMEOUT_MS = 8_000L

    /** Durée pendant laquelle l'île reste étendue toute seule (autoExpand). */
    const val AUTO_EXPAND_MS = 3_000L

    /** Nombre maximal de contenus simultanés par appli : le plus ancien cède sa place. */
    const val MAX_PER_APP = 3

    /** Nombre maximal de contenus simultanés, toutes applis confondues. */
    const val MAX_TOTAL = 12
}

/** Modèles de rendu (valeur de l'extra `template`, en minuscules). */
enum class ExternalTemplate(val wire: String) {
    STANDARD("standard"),
    PROGRESS("progress"),
    COUNTDOWN("countdown"),
    CHRONOMETER("chronometer"),
    LIST("list"),
    STATUS("status"),
    HEART("heart"),
    BREATHING("breathing"),
    ;

    companion object {
        fun parse(value: String?): ExternalTemplate? =
            value?.trim()?.lowercase()?.let { v -> entries.firstOrNull { it.wire == v } }
    }
}

/** État d'un service (modèle `status`). */
enum class ExternalStatus(val wire: String) {
    OK("ok"),
    WARN("warn"),
    DOWN("down"),
    ;

    companion object {
        fun parse(value: String?): ExternalStatus? =
            value?.trim()?.lowercase()?.let { v -> entries.firstOrNull { it.wire == v } }
    }
}

/** Priorités accessibles aux applis (jamais celle d'un appel ni d'une alarme). */
enum class ExternalPriority(val wire: String) {
    MEDIA("media"),
    LIVE("live"),
    ALERT("alert"),
    ;

    companion object {
        fun parse(value: String?): ExternalPriority? =
            value?.trim()?.lowercase()?.let { v -> entries.firstOrNull { it.wire == v } }
    }
}
