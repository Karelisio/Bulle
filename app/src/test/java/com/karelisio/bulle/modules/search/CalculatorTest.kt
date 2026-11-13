package com.karelisio.bulle.modules.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

/** Parseur de la calculatrice : priorités, parenthèses, fonctions, virgule, erreurs, mise en forme. */
class CalculatorTest {

    private fun eval(s: String): String {
        val r = Calculator.evaluate(s)
        check(r is CalcResult.Value) { "« $s » → $r" }
        return Calculator.format(r.value, Locale.ROOT)
    }

    private fun error(s: String): CalcError? = (Calculator.evaluate(s) as? CalcResult.Error)?.reason

    @Test
    fun precedenceAndParentheses() {
        assertEquals("14", eval("2+3×4"))
        assertEquals("20", eval("(2+3)×4"))
        assertEquals("2.5", eval("10/4"))
        assertEquals("2.5", eval("10 ÷ 4"))
        assertEquals("-4", eval("-2^2"))
        assertEquals("512", eval("2^3^2"))
        assertEquals("1", eval("10 − 3 * 3"))
        assertEquals("12", eval("3x4"))
        assertEquals("8", eval("2(3+1)"))
        assertEquals("5", eval("(2+3")) // parenthèse non fermée pendant la frappe
        assertEquals("0.25", eval("2^-2"))
    }

    @Test
    fun decimalsWithCommaOrPoint() {
        assertEquals("3", eval("1,5+1.5"))
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("0.333333333333", eval("1/3"))
        assertEquals("7.5", eval("2,5 × 3"))
    }

    @Test
    fun percentFunctionsAndConstants() {
        assertEquals("12", eval("15 % de 80"))
        assertEquals("12", eval("15% of 80"))
        assertEquals("50.1", eval("50+10%"))
        assertEquals("5", eval("sqrt(16)+1"))
        assertEquals("3", eval("√9"))
        assertEquals("6.28318530718", eval("2π"))
        assertEquals("3.14159265359", eval("pi"))
        assertEquals("0.5", eval("sin(30°)"))
        assertEquals("0", eval("sin(π)"))
        assertEquals("1", eval("cos(0)"))
        assertEquals("1", eval("tan(45°)"))
        assertEquals("3", eval("log(1000)"))
        assertEquals("1", eval("ln(e)"))
    }

    @Test
    fun errors() {
        assertEquals(CalcError.DIVISION_BY_ZERO, error("1/0"))
        assertEquals(CalcError.SYNTAX, error("2+"))
        assertEquals(CalcError.SYNTAX, error("2+)"))
        assertEquals(CalcError.SYNTAX, error(""))
        assertEquals(CalcError.SYNTAX, error("2 pommes"))
        assertEquals(CalcError.DOMAIN, error("sqrt(-1)"))
        assertEquals(CalcError.DOMAIN, error("ln(0)"))
        assertEquals(CalcError.OVERFLOW, error("10^999"))
    }

    @Test
    fun onlyExpressionsAreShown() {
        assertTrue(Calculator.looksLikeExpression("2+2"))
        assertTrue(Calculator.looksLikeExpression("sqrt 2"))
        assertFalse(Calculator.looksLikeExpression("42"))
        assertFalse(Calculator.looksLikeExpression("spotify"))
        assertFalse(Calculator.looksLikeExpression("5 km en miles"))
        assertFalse(Calculator.looksLikeExpression("sin"))
        assertNull(Calculator.evaluateOrNull("2+"))
        assertEquals(BigDecimal(4), Calculator.evaluateOrNull("2+2"))
    }

    @Test
    fun formatting() {
        assertEquals("1234,5", Calculator.format(BigDecimal("1234.5"), Locale.FRANCE))
        assertEquals("1234.5", Calculator.format(BigDecimal("1234.5"), Locale.US))
        assertEquals("1e20", Calculator.format(BigDecimal("1E20"), Locale.US))
        assertEquals("0", Calculator.format(BigDecimal("0.000"), Locale.US))
    }
}
