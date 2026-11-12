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

/** Alerte de fiabilité : postée aussi sous Android 12 (pas de POST_NOTIFICATIONS avant Android 13). */
@RunWith(RobolectricTestRunner::class)
class VoletReliabilityNotifyTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val nm get() = context.getSystemService(NotificationManager::class.java)

    @Test
    @Config(sdk = [32])
    fun postedOnAndroid12WithoutRuntimePermission() {
        VoletReliability.notifyIfNeeded(context, listOf(VoletIssue.ACCESSIBILITY_KILLED))
        assertEquals(1, shadowOf(nm).allNotifications.size)
        VoletReliability.notifyIfNeeded(context, emptyList())
        assertEquals(0, shadowOf(nm).allNotifications.size)
    }

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
