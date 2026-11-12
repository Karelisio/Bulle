package com.karelisio.bulle.island

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundAppTest {
    @Test
    fun keyboardWindowDoesNotReplaceForegroundApp() {
        ForegroundApp.inputMethods = setOf("com.example.keyboard")
        ForegroundApp.onWindowChanged("com.example.game")
        ForegroundApp.onWindowChanged("com.example.keyboard")
        ForegroundApp.onWindowChanged("com.android.systemui")
        assertEquals("com.example.game", ForegroundApp.packageName.value)
        ForegroundApp.inputMethods = emptySet()
    }
}
