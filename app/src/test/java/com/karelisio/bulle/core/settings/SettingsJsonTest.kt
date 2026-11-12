package com.karelisio.bulle.core.settings

import com.karelisio.bulle.core.actions.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsJsonTest {
    @Test
    fun unknownEnumValueFallsBackToDefaultInsteadOfResettingEverything() {
        // Réglages écrits par une version plus récente (nouvelle forme, nouvelle action).
        val json = """
            {"islandEnabled": false,
             "style": {"shape": "FUTURE_SHAPE", "size": 1.3},
             "gestures": {"tap": {"type": "FUTURE_ACTION"}, "doubleTap": {"type": "HOME"}}}
        """.trimIndent()
        val s = BulleJson.decodeFromString(BulleSettings.serializer(), json)
        assertFalse(s.islandEnabled)
        assertEquals(1.3f, s.style.size)
        assertEquals(IslandShape.PILL, s.style.shape)
        assertEquals(ActionType.NONE, s.gestures.tap.type)
        assertEquals(ActionType.HOME, s.gestures.doubleTap.type)
    }
}
