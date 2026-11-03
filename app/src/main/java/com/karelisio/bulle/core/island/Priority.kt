package com.karelisio.bulle.core.island

/**
 * Priorité d'un contenu de l'île. Valeur entière pour pouvoir intercaler
 * de futurs types de contenus sans toucher aux existants.
 * Ordre : appel > alarme > alerte ponctuelle > live update > média > repos.
 */
@JvmInline
value class Priority(val value: Int) : Comparable<Priority> {
    override fun compareTo(other: Priority): Int = value.compareTo(other.value)

    companion object {
        val IDLE = Priority(0)
        val MEDIA = Priority(100)
        val LIVE = Priority(200)
        val ALERT = Priority(300)
        val ALARM = Priority(400)
        val CALL = Priority(500)
    }
}
