package com.karelisio.bulle.modules.volet

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.karelisio.bulle.core.actions.GestureAction

/** Côté par lequel le volet a été ouvert. */
enum class VoletZone { NOTIFICATIONS, QUICK_SETTINGS }

/**
 * Ce que le volet met à disposition de ses sections (notifications, réglages rapides, média).
 * Les propriétés sont observables en Compose (adossées à des états Compose).
 */
interface VoletHost {
    val context: Context

    /** Ouverture : 0 fermé, 1 à demi (notifications + rangée de tuiles), 2 complet (grille). */
    val expansion: Float

    /** Ouverture des réglages rapides, de 0 (rangée) à 1 (grille complète). */
    val qsExpansion: Float get() = (expansion - 1f).coerceIn(0f, 1f)

    val zone: VoletZone
    val settings: VoletSettings

    /** Appareil verrouillé : masquer le contenu sensible. */
    val locked: Boolean

    /** Notification à mettre en avant (ouverte depuis l'île), ou null. */
    val focusKey: String?

    /** Mode édition des tuiles. */
    var editingTiles: Boolean

    fun close()
    fun expandFull()
    fun collapseToHalf()

    /** Déplacement du doigt dans le volet (pixels, positif vers le bas). */
    fun dragBy(dyPx: Float)

    /** Fin du glissement avec sa vitesse (pixels/s). */
    fun settle(velocityPxPerSec: Float)

    /** Déclenche un PendingIntent (avec intent complémentaire éventuel), puis ferme si demandé. */
    fun send(intent: PendingIntent?, fillIn: Intent? = null, closeAfter: Boolean = true): Boolean

    /** Lance une activité (réglages…) et ferme le volet. */
    fun startActivity(intent: Intent)

    /** Exécute une action de geste (capture, verrouillage, lampe…). */
    fun runAction(action: GestureAction)

    /** Rend le volet focusable pour une saisie (réponse directe). */
    fun requestKeyboard(show: Boolean)

    /** Ouvre le volet du système (repli). */
    fun openSystemShade()
}
