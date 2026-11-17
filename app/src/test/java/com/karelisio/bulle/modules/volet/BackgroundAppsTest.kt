package com.karelisio.bulle.modules.volet

import com.karelisio.bulle.modules.volet.background.BackgroundApps
import com.karelisio.bulle.modules.volet.background.ServiceNotif
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundAppsTest {
    @Test
    fun groupsByAppOldestFirstWithoutBulle() {
        val apps = BackgroundApps.group(
            listOf(
                ServiceNotif("com.music", "Lecture", "Artiste", 300),
                ServiceNotif("com.sync", "Synchronisation…", null, 100),
                ServiceNotif("com.music", "Lecture", "Téléchargement", 200),
                ServiceNotif("com.karelisio.bulle", "Bulle", null, 50),
            ),
            own = "com.karelisio.bulle",
        )
        assertEquals(listOf("com.sync", "com.music"), apps.map { it.packageName })
        assertEquals(listOf("Lecture", "Artiste", "Téléchargement"), apps[1].details)
        assertEquals(200L, apps[1].since)
    }
}
