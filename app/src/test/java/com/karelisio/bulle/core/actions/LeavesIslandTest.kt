package com.karelisio.bulle.core.actions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeavesIslandTest {
    @Test
    fun screenOpeningActionsCloseTheIsland() {
        listOf(ActionType.SCAN_QR, ActionType.SCREEN_RECORD, ActionType.MEMO, ActionType.OPEN_APP, ActionType.SCREENSHOT, ActionType.WIFI)
            .forEach { assertTrue(it.name, ActionExecutor.leavesIsland(it)) }
        listOf(ActionType.TORCH, ActionType.PLAY_PAUSE, ActionType.VOLUME_UP, ActionType.TRANSLATE, ActionType.OPEN_DECK)
            .forEach { assertFalse(it.name, ActionExecutor.leavesIsland(it)) }
    }
}
