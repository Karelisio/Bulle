package com.karelisio.bulle.modules.focus

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Persistance de l'état du minuteur (survit au redémarrage du service). */
class FocusStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun roundTrip() {
        val file = tmp.newFile("focus.json")
        val store = FocusStore(file)
        val data = FocusData(
            settings = FocusSettings(workMinutes = 50, autoAdvance = false, sound = true),
            state = FocusState(FocusPhase.SHORT_BREAK, cycle = 2, cycles = 4, durationMs = 300_000, endAtWall = 123_456L),
        )
        store.save(data)
        assertEquals(data, FocusStore(file).load())
    }

    @Test
    fun missingOrCorruptFileGivesDefaults() {
        assertEquals(FocusData(), FocusStore(java.io.File(tmp.root, "absent.json")).load())
        val bad = tmp.newFile("bad.json").apply { writeText("{pas du json") }
        assertEquals(FocusData(), FocusStore(bad).load())
    }

    @Test
    fun unknownFieldsAndMissingFieldsAreTolerated() {
        val f = tmp.newFile("old.json").apply { writeText("""{"settings":{"cycles":3,"futur":true},"state":{"phase":"WORK","durationMs":1000}}""") }
        val d = FocusStore(f).load()
        assertEquals(3, d.settings.cycles)
        assertEquals(25, d.settings.workMinutes)
        assertEquals(FocusPhase.WORK, d.state?.phase)
    }

    @Test
    fun focusRestoresStateFromFile() {
        val file = tmp.newFile("restore.json")
        val state = FocusState(FocusPhase.WORK, cycle = 3, cycles = 4, durationMs = 1_500_000, endAtWall = 9_999L)
        FocusStore(file).save(FocusData(state = state))
        Focus.resetForTests(file)
        assertEquals(state, Focus.data.value.state)
        Focus.resetForTests()
    }
}
