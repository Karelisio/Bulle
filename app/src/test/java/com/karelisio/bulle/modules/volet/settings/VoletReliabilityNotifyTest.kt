package com.karelisio.bulle.modules.volet.settings

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Alerte de fiabilité : autorisation demandée à partir d'Android 13.
 * (Le cas Android 12 est vérifié sans Robolectric dans VoletReliabilityAlertTest : un bac à sable
 * SDK 32 dans la même JVM que les SDK 35 casse le chargement natif de Robolectric pour les suivants.)
 */
@RunWith(RobolectricTestRunner::class)
class VoletReliabilityNotifyTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val nm get() = context.getSystemService(NotificationManager::class.java)

    @Test
    @Config(sdk = [35])
    fun needsPermissionOnAndroid13Plus() {
        VoletReliability.notifyIfNeeded(context, listOf(VoletIssue.LISTENER_DISCONNECTED))
        assertEquals(0, shadowOf(nm).allNotifications.size)
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        VoletReliability.notifyIfNeeded(context, listOf(VoletIssue.LISTENER_DISCONNECTED))
        assertEquals(1, shadowOf(nm).allNotifications.size)
    }
}
