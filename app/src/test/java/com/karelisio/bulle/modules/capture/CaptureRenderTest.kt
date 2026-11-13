package com.karelisio.bulle.modules.capture

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.island.IslandGeometry
import com.karelisio.bulle.island.IslandWindow
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.Modules
import com.karelisio.bulle.modules.capture.memo.MemoContent
import com.karelisio.bulle.modules.capture.memo.MemoStore
import com.karelisio.bulle.modules.capture.qr.QrParser
import com.karelisio.bulle.modules.capture.qr.QrResultContent
import com.karelisio.bulle.modules.capture.qr.QrScan
import com.karelisio.bulle.modules.capture.qr.ScanFrame
import com.karelisio.bulle.modules.capture.recorder.ScreenRecordContent
import com.karelisio.bulle.modules.capture.ui.CaptureSettingsScreen
import com.karelisio.bulle.modules.capture.ui.MemosScreen
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Capture : contenus d'île (compact / étendu), écrans de réglages et des mémos, module, réglages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptureRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before
    fun setUp() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        CaptureState.resetForTests()
        CapturePrefs.resetForTests()
    }

    @After
    fun tearDown() {
        CaptureState.resetForTests()
        CapturePrefs.resetForTests()
    }

    @Test
    fun contentsRenderCompactAndExpanded() {
        Modules.registerAll()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val scope = MainScope()
        val controller = IslandController(activity, scope, IslandWindow(activity))
        controller.updateScreen {
            it.copy(geometry = IslandGeometry(centerX = 540, top = 10, width = 180, height = 90, screenWidth = 1080, density = 3f))
        }
        activity.setContent { IslandScene(controller) }
        idle()

        val now = SystemClock.elapsedRealtime()
        val recording = MemoMachine.reduce(MemoSession(), MemoEvent.Start(now - 65_000))
        val contents: List<IslandContent> = listOf(
            MemoContent(recording),
            MemoContent(MemoMachine.reduce(recording, MemoEvent.Pause(now))),
            ScreenRecordContent(RecorderSession(RecorderPhase.RECORDING, now - 3_000, withAudio = true)),
        ) + listOf(
            "https://exemple.fr",
            "WIFI:T:WPA;S:Maison;P:secret;;",
            "BEGIN:VCARD\nFN:Jean Dupont\nTEL:0102030405\nEND:VCARD",
            "tel:0102030405",
            "SMSTO:0600000000:Salut",
            "mailto:a@b.fr",
            "geo:48.85,2.35",
            "Un texte libre",
        ).mapIndexed { i, raw -> QrResultContent(QrParser.parse(raw), postedAt = now + i) }

        for (c in contents) {
            PushModule.push(c)
            idle()
            CaptureState.memoLevel(0.6f)
            idle()
            assertEquals(c.key, controller.state.value.primary?.key)
            controller.onGesture(IslandGesture.TAP)
            idle()
            controller.onOutsideTouch()
            idle()
            PushModule.remove(c.key)
            idle()
        }
        scope.cancel()
    }

    @Test
    fun moduleEmitsActiveContents() = runBlocking {
        val env = com.karelisio.bulle.core.island.ModuleEnv(
            context,
            MainScope(),
            kotlinx.coroutines.flow.MutableStateFlow(com.karelisio.bulle.core.settings.BulleSettings()),
        )
        assertTrue(CaptureModule.contents(env).first().isEmpty())
        CaptureState.memo(MemoEvent.Start(SystemClock.elapsedRealtime()))
        CaptureState.screen(RecorderEvent.Started(SystemClock.elapsedRealtime(), withAudio = false))
        CaptureState.showQr(QrParser.parse("https://exemple.fr"))
        val keys = CaptureModule.contents(env).first().map { it.key }
        assertEquals(3, keys.size)
        assertTrue(MemoContent.KEY in keys && ScreenRecordContent.KEY in keys)
        // Pause : niveau remis à zéro.
        CaptureState.memoLevel(0.8f)
        CaptureState.memo(MemoEvent.Pause(SystemClock.elapsedRealtime()))
        assertEquals(0f, CaptureState.memoLevel.value, 0f)
        CaptureState.memo(MemoEvent.Stop(0))
        CaptureState.screen(RecorderEvent.Stopped)
        CaptureState.clearQr()
        assertTrue(CaptureModule.contents(env).first().isEmpty())
    }

    @Test
    fun taggedScanResultsReachRequester() = runBlocking {
        val pending = async(start = CoroutineStart.UNDISPATCHED) { QrScan.results.first() }
        QrScan.deliver("style-import", "bulle://style/abc")
        assertEquals("style-import" to "bulle://style/abc", pending.await())
    }

    @Test
    fun settingsPersist() {
        CapturePrefs.update(context) { it.copy(videoQuality = VideoQuality.HD, screenMicAudio = true) }
        val f = File(context.filesDir, CapturePrefs.FILE_NAME)
        CapturePrefs.write(f, CapturePrefs.data.value)
        assertEquals(VideoQuality.HD, CapturePrefs.read(f).videoQuality)
        assertTrue(CapturePrefs.read(f).screenMicAudio)
    }

    @Test
    fun memoStoreListsRenamesDeletes() {
        val dir = MemoStore.dir(context)
        dir.listFiles()?.forEach { it.delete() }
        val f = MemoStore.newFile(context, "Mémo", now = 1_000L)
        f.writeText("x")
        val memo = MemoStore.list(context).single()
        assertTrue(MemoStore.rename(context, memo, "Courses"))
        val renamed = MemoStore.list(context).single()
        assertEquals("Courses", renamed.name)
        assertFalse(MemoStore.rename(context, renamed, "  "))
        assertTrue(MemoStore.delete(renamed))
        assertTrue(MemoStore.list(context).isEmpty())
    }

    private fun render(content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { BulleTheme { content() } }
        idle()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
        idle()
    }

    @Test
    fun screensRender() {
        MemoStore.newFile(context, "Mémo").writeText("pas un vrai m4a")
        render { CaptureSettingsScreen(onBack = {}) }
        render { MemosScreen(onBack = {}) }
        render { ScanFrame(detected = false, reduceMotion = true) }
        render { ScanFrame(detected = true, reduceMotion = false) }
    }
}
