package com.karelisio.bulle.modules.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/** Conversions d'unités : analyse FR/EN de la requête et toutes les catégories. */
class UnitConverterTest {

    private fun conv(s: String): String {
        val c = UnitConverter.evaluate(s) ?: error("« $s » non reconnu")
        return UnitConverter.display(c.result).toPlainString()
    }

    @Test
    fun parsesFrenchAndEnglishConnectors() {
        val q = UnitConverter.parse("5 km en miles")!!
        assertEquals(BigDecimal(5), q.amount)
        assertEquals("km", q.from.id)
        assertEquals("mi", q.to.id)
        assertNotNull(UnitConverter.parse("5 km in miles"))
        assertNotNull(UnitConverter.parse("5 km to mi"))
        assertNotNull(UnitConverter.parse("5km → mi"))
        assertNotNull(UnitConverter.parse("5 km -> mi"))
        assertNotNull(UnitConverter.parse("5 kilomètres vers miles"))
        assertEquals(BigDecimal("5.5"), UnitConverter.parse("5,5 m en cm")!!.amount)
        // « in » est aussi le pouce : le bon connecteur est retrouvé.
        assertEquals("in", UnitConverter.parse("5 in en cm")!!.from.id)
        assertEquals("in", UnitConverter.parse("3 ft to in")!!.to.id)
    }

    @Test
    fun rejectsNonConversions() {
        assertNull(UnitConverter.parse("5 km en kg")) // catégories différentes
        assertNull(UnitConverter.parse("5 km"))
        assertNull(UnitConverter.parse("km en miles"))
        assertNull(UnitConverter.parse("spotify"))
        assertNull(UnitConverter.parse("20 € en $"))
        assertNull(UnitConverter.parse(""))
    }

    @Test
    fun lengthMassVolume() {
        assertEquals("3.106855961", conv("5 km en miles"))
        assertEquals("1.609344", conv("1 mile → km"))
        assertEquals("36", conv("3 ft to in"))
        assertEquals("550", conv("5,5 m en cm"))
        assertEquals("0.45359237", conv("1 lb in kg"))
        assertEquals("1000", conv("1 t en kg"))
        assertEquals("1000", conv("1 l en ml"))
        assertEquals("3.785411784", conv("1 gallon in liters"))
    }

    @Test
    fun temperature() {
        assertEquals("86", conv("30 °C en F"))
        assertEquals("86", conv("30°C to F"))
        assertEquals("37.77777778", conv("100 F en C"))
        assertEquals("273.15", conv("0 c in k"))
        assertEquals("-40", conv("-40 celsius en fahrenheit"))
    }

    @Test
    fun areaSpeedDataTime() {
        assertEquals("10000", conv("1 ha en m²"))
        assertEquals("10000", conv("1 ha en m2"))
        assertEquals("62.13711922", conv("100 km/h en mph"))
        assertEquals("3.6", conv("1 m/s en km/h"))
        assertEquals("1000", conv("1 go en mo"))
        assertEquals("1024", conv("1 gio en mio"))
        assertEquals("8", conv("1 octet en bits"))
        assertEquals("120", conv("2 h en min"))
        assertEquals("24", conv("1 jour en heures"))
        assertEquals("7", conv("1 week in days"))
    }
}
