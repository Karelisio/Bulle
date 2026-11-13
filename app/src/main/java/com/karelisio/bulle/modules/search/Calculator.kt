package com.karelisio.bulle.modules.search

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Résultat d'un calcul : valeur, ou erreur (expression incomplète, division par zéro…). */
sealed interface CalcResult {
    data class Value(val value: BigDecimal) : CalcResult
    data class Error(val reason: CalcError) : CalcResult
}

enum class CalcError { SYNTAX, DIVISION_BY_ZERO, DOMAIN, OVERFLOW }

/**
 * Calculatrice de la recherche : parseur maison à descente récursive (aucun eval), fonction pure.
 *
 * Opérateurs : + − × ÷ (et * / x ·), ^, % (pourcentage postfixe), parenthèses, multiplication implicite
 * (« 2π », « 3(4+1) »). Décimales à virgule ou à point. Fonctions sqrt/√, sin, cos, tan (radians, ou
 * degrés avec « ° »), log (base 10), ln ; constantes π/pi et e. Précision : 16 chiffres significatifs
 * (les fonctions transcendantes passent par Double, arrondies à 12 chiffres).
 */
object Calculator {
    private val MC = MathContext.DECIMAL64
    private val FUNCTIONS = setOf("sqrt", "sin", "cos", "tan", "log", "ln")
    private val PI = BigDecimal("3.141592653589793")
    private val E = BigDecimal("2.718281828459045")
    private val MAX = BigDecimal("1e100")

    /**
     * Vrai si la saisie ressemble à un calcul (au moins un opérateur, une fonction ou une constante,
     * et rien d'autre que des éléments d'expression) : évite d'afficher « 42 » pour une simple saisie.
     */
    fun looksLikeExpression(input: String): Boolean {
        val s = input.trim()
        if (s.isEmpty()) return false
        val tokens = runCatching { tokenize(s) }.getOrNull() ?: return false
        if (tokens.isEmpty()) return false
        return tokens.any { it is Tok.Op || it is Tok.Func || it is Tok.Const || it is Tok.Postfix } &&
            tokens.any { it is Tok.Num || it is Tok.Const }
    }

    /** Évalue [input]. Ne lève jamais d'exception. */
    fun evaluate(input: String): CalcResult = try {
        val parser = Parser(tokenize(input.trim()))
        val v = parser.parseAll()
        if (v.abs() > MAX) CalcResult.Error(CalcError.OVERFLOW) else CalcResult.Value(v.round(MC).stripZeros())
    } catch (e: CalcException) {
        CalcResult.Error(e.reason)
    } catch (e: ArithmeticException) {
        CalcResult.Error(CalcError.OVERFLOW)
    }

    /** Évalue seulement si la saisie ressemble à un calcul et aboutit ; sinon null. */
    fun evaluateOrNull(input: String): BigDecimal? =
        if (!looksLikeExpression(input)) null else (evaluate(input) as? CalcResult.Value)?.value

    /**
     * Mise en forme : 12 chiffres significatifs au plus, séparateur décimal de la langue,
     * notation scientifique au-delà de 1e15 ou en deçà de 1e-9.
     */
    fun format(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val sep = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(12, RoundingMode.HALF_EVEN)).stripZeros()
        val abs = rounded.abs()
        val text = if (abs >= BigDecimal("1e15") || abs < BigDecimal("1e-9")) {
            rounded.toString().replace("E+", "e").replace("E", "e")
        } else {
            rounded.toPlainString()
        }
        return text.replace('.', sep)
    }

    // --- Analyse lexicale ---

    private sealed interface Tok {
        data class Num(val value: BigDecimal) : Tok
        data class Op(val c: Char) : Tok // + - * / ^
        data class Func(val name: String) : Tok
        data class Const(val value: BigDecimal) : Tok
        data class Postfix(val c: Char) : Tok // % °
        data object LParen : Tok
        data object RParen : Tok
    }

    private class CalcException(val reason: CalcError) : Exception()

    private fun tokenize(s: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() || c == ' ' || c == ' ' -> i++
                c.isDigit() || ((c == '.' || c == ',') && i + 1 < s.length && s[i + 1].isDigit()) -> {
                    val start = i
                    var sawSep = false
                    while (i < s.length && (s[i].isDigit() || ((s[i] == '.' || s[i] == ',') && !sawSep))) {
                        if (s[i] == '.' || s[i] == ',') {
                            // Un séparateur doit être suivi d'un chiffre (« 2, » n'est pas un nombre décimal).
                            if (i + 1 >= s.length || !s[i + 1].isDigit()) break
                            sawSep = true
                        }
                        i++
                    }
                    out += Tok.Num(BigDecimal(s.substring(start, i).replace(',', '.')))
                }
                c == '+' -> { out += Tok.Op('+'); i++ }
                c == '-' || c == '−' || c == '–' -> { out += Tok.Op('-'); i++ }
                c == '*' || c == '×' || c == '·' || c == '∙' -> { out += Tok.Op('*'); i++ }
                c == 'x' && out.lastOrNull().let { it is Tok.Num || it is Tok.RParen || it is Tok.Const || it is Tok.Postfix } &&
                    i + 1 < s.length && s.substring(i + 1).trimStart().firstOrNull()?.let { it.isDigit() || it == '(' } == true -> {
                    out += Tok.Op('*'); i++
                }
                c == '/' || c == '÷' || c == ':' -> { out += Tok.Op('/'); i++ }
                c == '^' -> { out += Tok.Op('^'); i++ }
                c == '%' || c == '°' -> { out += Tok.Postfix(c); i++ }
                c == '(' || c == '[' -> { out += Tok.LParen; i++ }
                c == ')' || c == ']' -> { out += Tok.RParen; i++ }
                c == 'π' -> { out += Tok.Const(PI); i++ }
                c == '√' -> { out += Tok.Func("sqrt"); i++ }
                c.isLetter() -> {
                    val start = i
                    while (i < s.length && s[i].isLetter()) i++
                    when (val word = s.substring(start, i).lowercase(Locale.ROOT)) {
                        "pi" -> out += Tok.Const(PI)
                        "e" -> out += Tok.Const(E)
                        "racine", "rac" -> out += Tok.Func("sqrt")
                        // « 15 % de 80 » / « 15% of 80 ».
                        "de", "of" -> if (out.lastOrNull() == Tok.Postfix('%')) out += Tok.Op('*') else throw CalcException(CalcError.SYNTAX)
                        in FUNCTIONS -> out += Tok.Func(word)
                        else -> throw CalcException(CalcError.SYNTAX)
                    }
                }
                else -> throw CalcException(CalcError.SYNTAX)
            }
        }
        return out
    }

    // --- Analyse syntaxique (priorités : +− < ×÷ < unaire < ^ < postfixe) ---

    private class Parser(private val toks: List<Tok>) {
        private var pos = 0
        private fun peek(): Tok? = toks.getOrNull(pos)

        fun parseAll(): BigDecimal {
            if (toks.isEmpty()) throw CalcException(CalcError.SYNTAX)
            val v = expr()
            if (pos != toks.size) throw CalcException(CalcError.SYNTAX)
            return v
        }

        private fun expr(): BigDecimal {
            var v = term()
            while (true) {
                val t = peek()
                if (t is Tok.Op && (t.c == '+' || t.c == '-')) {
                    pos++
                    val r = term()
                    v = if (t.c == '+') v.add(r, MC) else v.subtract(r, MC)
                } else {
                    return v
                }
            }
        }

        private fun startsOperand(t: Tok?) = t is Tok.Num || t is Tok.Const || t is Tok.Func || t is Tok.LParen

        private fun term(): BigDecimal {
            var v = unary()
            while (true) {
                val t = peek()
                when {
                    t is Tok.Op && (t.c == '*' || t.c == '/') -> {
                        pos++
                        val r = unary()
                        v = if (t.c == '*') v.multiply(r, MC) else divide(v, r)
                    }
                    // Multiplication implicite : « 2π », « 3(1+1) », « 2 sqrt 9 ».
                    startsOperand(t) -> v = v.multiply(unary(), MC)
                    else -> return v
                }
            }
        }

        private fun unary(): BigDecimal {
            val t = peek()
            if (t is Tok.Op && (t.c == '-' || t.c == '+')) {
                pos++
                val v = unary()
                return if (t.c == '-') v.negate() else v
            }
            return power()
        }

        private fun power(): BigDecimal {
            val base = postfix()
            val t = peek()
            if (t is Tok.Op && t.c == '^') {
                pos++
                return pow(base, unary()) // associatif à droite : 2^3^2 = 2^9
            }
            return base
        }

        private fun postfix(): BigDecimal {
            var v = primary()
            while (true) {
                val t = peek()
                if (t is Tok.Postfix) {
                    pos++
                    v = if (t.c == '%') v.divide(BigDecimal(100), MC) else v.multiply(PI, MC).divide(BigDecimal(180), MC)
                } else {
                    return v
                }
            }
        }

        private fun primary(): BigDecimal {
            return when (val t = peek()) {
                is Tok.Num -> { pos++; t.value }
                is Tok.Const -> { pos++; t.value }
                is Tok.LParen -> {
                    pos++
                    val v = expr()
                    // Parenthèse fermante facultative en fin de saisie (« (2+3 » pendant la frappe).
                    if (peek() is Tok.RParen) pos++ else if (peek() != null) throw CalcException(CalcError.SYNTAX)
                    v
                }
                is Tok.Func -> {
                    pos++
                    applyFunction(t.name, power())
                }
                else -> throw CalcException(CalcError.SYNTAX)
            }
        }
    }

    private fun divide(a: BigDecimal, b: BigDecimal): BigDecimal {
        if (b.signum() == 0) throw CalcException(CalcError.DIVISION_BY_ZERO)
        return a.divide(b, MC)
    }

    private fun pow(base: BigDecimal, exp: BigDecimal): BigDecimal {
        val asInt = runCatching { exp.intValueExact() }.getOrNull()
        if (asInt != null && kotlin.math.abs(asInt) <= 999) {
            if (asInt < 0 && base.signum() == 0) throw CalcException(CalcError.DIVISION_BY_ZERO)
            return if (asInt >= 0) base.pow(asInt, MC) else BigDecimal.ONE.divide(base.pow(-asInt, MC), MC)
        }
        val r = Math.pow(base.toDouble(), exp.toDouble())
        return fromDouble(r)
    }

    private fun applyFunction(name: String, x: BigDecimal): BigDecimal {
        val d = x.toDouble()
        val r = when (name) {
            "sqrt" -> if (d < 0) throw CalcException(CalcError.DOMAIN) else Math.sqrt(d)
            "sin" -> Math.sin(d)
            "cos" -> Math.cos(d)
            "tan" -> {
                if (kotlin.math.abs(Math.cos(d)) < 1e-12) throw CalcException(CalcError.DOMAIN)
                Math.tan(d)
            }
            "log" -> if (d <= 0) throw CalcException(CalcError.DOMAIN) else Math.log10(d)
            "ln" -> if (d <= 0) throw CalcException(CalcError.DOMAIN) else Math.log(d)
            else -> throw CalcException(CalcError.SYNTAX)
        }
        return fromDouble(r)
    }

    /** Double → BigDecimal arrondi à 12 chiffres (sin(π) = 0, sqrt(2)² = 2). */
    private fun fromDouble(d: Double): BigDecimal {
        if (d.isNaN()) throw CalcException(CalcError.DOMAIN)
        if (d.isInfinite()) throw CalcException(CalcError.OVERFLOW)
        if (kotlin.math.abs(d) < 1e-13) return BigDecimal.ZERO
        return BigDecimal(d).round(MathContext(12, RoundingMode.HALF_EVEN))
    }

    private fun BigDecimal.stripZeros(): BigDecimal = if (signum() == 0) BigDecimal.ZERO else stripTrailingZeros()
}
