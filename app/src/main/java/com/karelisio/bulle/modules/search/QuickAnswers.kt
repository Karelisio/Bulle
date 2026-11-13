package com.karelisio.bulle.modules.search

import java.math.BigDecimal

/** Réponse instantanée affichée au-dessus des applis (au plus une par saisie). */
sealed interface QuickAnswer {
    data class Calc(val expression: String, val value: BigDecimal) : QuickAnswer
    data class Units(val conversion: UnitConversion) : QuickAnswer
    /** Devise : la conversion elle-même dépend des taux (chargés à part). */
    data class Money(val query: CurrencyQuery) : QuickAnswer
}

/** Aiguillage de la saisie vers les sections de la recherche (fonction pure). */
object QuickAnswers {
    fun of(query: String, settings: SearchSettings): QuickAnswer? {
        val q = query.trim()
        if (q.isEmpty()) return null
        if (settings.currency) Currency.parse(q)?.let { return QuickAnswer.Money(it) }
        if (settings.units) UnitConverter.evaluate(q)?.let { return QuickAnswer.Units(it) }
        if (settings.calculator) Calculator.evaluateOrNull(q)?.let { return QuickAnswer.Calc(q, it) }
        return null
    }

    /** Les contacts et réglages ne sont cherchés que pour du texte (pas pour un calcul ou une conversion). */
    fun isTextQuery(query: String, answer: QuickAnswer?): Boolean =
        answer == null && query.trim().length >= 2 && query.any { it.isLetter() }
}
