package com.karelisio.bulle.ui.onboarding

import android.Manifest
import com.karelisio.bulle.system.OemSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionLogicTest {

    @Test
    fun honorOnAndroid15GetsEveryStepInOrder() {
        assertEquals(
            listOf(
                PermissionKey.RESTRICTED,
                PermissionKey.ACCESSIBILITY,
                PermissionKey.NOTIFICATION_ACCESS,
                PermissionKey.POST_NOTIFICATIONS,
                PermissionKey.INSTALL_PACKAGES,
                PermissionKey.BATTERY,
                PermissionKey.OEM_LAUNCH,
            ),
            PermissionLogic.onboardingSteps(35, OemSettings.Family.HONOR),
        )
    }

    @Test
    fun android12StockSkipsRestrictedNotificationsAndOem() {
        val steps = PermissionLogic.onboardingSteps(31, OemSettings.Family.STOCK)
        assertFalse(PermissionKey.RESTRICTED in steps)
        assertFalse(PermissionKey.POST_NOTIFICATIONS in steps)
        assertFalse(PermissionKey.OEM_LAUNCH in steps)
        assertTrue(PermissionKey.BATTERY in steps)
    }

    @Test
    fun manufacturerFamilies() {
        assertEquals(OemSettings.Family.HONOR, OemSettings.familyOf("HONOR"))
        assertEquals(OemSettings.Family.HONOR, OemSettings.familyOf("Huawei"))
        assertEquals(OemSettings.Family.OTHER_AGGRESSIVE, OemSettings.familyOf("Xiaomi"))
        assertEquals(OemSettings.Family.STOCK, OemSettings.familyOf("Google"))
    }

    @Test
    fun serviceDiagnosis() {
        assertEquals(ServiceHealth.RUNNING, PermissionLogic.diagnose(enabledInSystem = true, running = true))
        assertEquals(ServiceHealth.KILLED, PermissionLogic.diagnose(enabledInSystem = true, running = false))
        assertEquals(ServiceHealth.DISABLED, PermissionLogic.diagnose(enabledInSystem = false, running = false))
    }

    @Test
    fun restrictedIsImpliedBySpecialAccess() {
        assertFalse(PermissionLogic.restrictedSatisfied(confirmed = false, accessibility = false, notificationAccess = false))
        assertTrue(PermissionLogic.restrictedSatisfied(confirmed = false, accessibility = true, notificationAccess = false))
        assertTrue(PermissionLogic.restrictedSatisfied(confirmed = true, accessibility = false, notificationAccess = false))
    }

    @Test
    fun runtimePermissions() {
        assertEquals(Manifest.permission.READ_MEDIA_IMAGES, PermissionLogic.runtimePermission(PermissionKey.MEDIA_IMAGES, 33))
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, PermissionLogic.runtimePermission(PermissionKey.MEDIA_IMAGES, 32))
        assertNull(PermissionLogic.runtimePermission(PermissionKey.POST_NOTIFICATIONS, 32))
        assertNull(PermissionLogic.runtimePermission(PermissionKey.BATTERY, 35))
        assertTrue(ADB_GRANT_COMMAND.endsWith("android.permission.WRITE_SECURE_SETTINGS"))
    }
}
