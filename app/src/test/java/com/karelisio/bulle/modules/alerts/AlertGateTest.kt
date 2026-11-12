package com.karelisio.bulle.modules.alerts

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** État « cochée ET autorisée » des sources : relance seulement quand il change. */
class AlertGateTest {

    @Test
    fun permissionGrantedLaterStartsSource() = runBlocking {
        // Permission refusée aux deux premiers battements, accordée ensuite (réglages système).
        var checks = 0
        val states = AlertsModule.gate(
            enabled = flowOf(true),
            permissions = listOf("p"),
            granted = { ++checks > 2 },
            ticks = flowOf(Unit, Unit, Unit, Unit, Unit),
        ).toList()
        // Une seule transition false → true, malgré cinq réévaluations.
        assertEquals(listOf(false, true), states)
    }

    @Test
    fun uncheckedStaysOffEvenIfGranted() = runBlocking {
        val states = AlertsModule.gate(flowOf(false), listOf("p"), { true }, flowOf(Unit, Unit)).toList()
        assertEquals(listOf(false), states)
    }

    @Test
    fun noPermissionFollowsCheckboxOnly() = runBlocking {
        var checks = 0
        val states = AlertsModule.gate(flowOf(true, true, false, true), emptyList(), { checks++; false }, flowOf(Unit)).toList()
        assertEquals(listOf(true, false, true), states)
        assertEquals(0, checks)
    }

    @Test
    fun usbChargeMergedOnlyForDataCableOnUsb() {
        assertEquals(true, SignalRules.mergeUsbCharge(usbAlertOn = true, plugged = SignalRules.PLUGGED_USB, dataCable = true))
        // Alerte USB coupée, chargeur secteur (AC = 1) ou câble sans données : alerte de charge seule.
        assertEquals(false, SignalRules.mergeUsbCharge(usbAlertOn = false, plugged = SignalRules.PLUGGED_USB, dataCable = true))
        assertEquals(false, SignalRules.mergeUsbCharge(usbAlertOn = true, plugged = 1, dataCable = true))
        assertEquals(false, SignalRules.mergeUsbCharge(usbAlertOn = true, plugged = SignalRules.PLUGGED_USB, dataCable = false))
        assertEquals(64, SignalRules.usbChargePercent(SignalRules.PLUGGED_USB, 64))
        assertEquals(null, SignalRules.usbChargePercent(0, 64))
    }
}
