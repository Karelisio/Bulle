package com.karelisio.bulle.modules.volet.settings

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Alerte de fiabilité balayée : pas reposée à chaque passage de la surveillance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoletReliabilitySnoozeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val nm get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun grant() {
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun swipedAlertIsNotRepostedUntilNatureChanges() {
        val t = 1_000_000L
        VoletReliability.notifyIfNeeded(context, listOf(VoletIssue.ACCESSIBILITY_KILLED), t)
        assertEquals(1, nm.activeNotifications.size)
        // Passage suivant, toujours affichée : pas de nouvelle pose.
        VoletReliability.notifyIfNeeded(context, listOf(VoletIssue.ACCESSIBILITY_KILLED), t + 1)
        assertEquals(1, nm.activeNotifications.size)
        // L'utilisateur la balaie : elle ne revient pas au passage suivant.
        nm.cancelAll()
        VoletReliability.notifyIfNeeded(context, listOf(VoletIssue.ACCESSIBILITY_KILLED), t + 2)
        assertEquals(0, nm.activeNotifications.size)
        // Autre nature de problème : reposée.
        VoletReliability.notifyIfNeeded(context, listOf(VoletIssue.LISTENER_DISABLED), t + 3)
        assertEquals(1, nm.activeNotifications.size)
        // Tout va bien : retirée.
        VoletReliability.notifyIfNeeded(context, emptyList(), t + 4)
        assertEquals(0, nm.activeNotifications.size)
    }
}
