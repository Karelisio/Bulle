package com.karelisio.bulle.core.apps

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppCatalogTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun launcherEntry(pkg: String): ResolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = pkg
            name = "$pkg.Main"
            applicationInfo = ApplicationInfo().apply { packageName = pkg }
            nonLocalizedLabel = pkg
        }
        nonLocalizedLabel = pkg
    }

    @Test
    fun cacheIsInvalidatedWhenAPackageIsInstalled() = runBlocking {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = shadowOf(app.packageManager)
        pm.addResolveInfoForIntent(launcher, launcherEntry("com.example.first"))
        AppCatalog.apps(app, refresh = true)

        pm.addResolveInfoForIntent(launcher, launcherEntry("com.example.second"))
        app.sendBroadcast(Intent(Intent.ACTION_PACKAGE_ADDED, Uri.parse("package:com.example.second")))
        shadowOf(Looper.getMainLooper()).idle()

        val names = AppCatalog.apps(app).map { it.packageName }
        assertTrue(names.toString(), "com.example.second" in names)
    }

    @Test
    fun launchOfAMissingActivityDoesNotThrow() {
        shadowOf(app).checkActivities(true)
        val entry = AppEntry("x", "com.example.gone", ComponentName("com.example.gone", "com.example.gone.Main"))
        assertFalse(AppCatalog.launch(app, entry))
    }
}
