package com.karelisio.bulle.modules.messages

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** Du flux de notifications au contenu de l'île : nouveau message annoncé, mise à jour silencieuse ignorée. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessagesModuleFlowTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        MessagesPrefs.resetForTests()
    }

    private fun sbn(pkg: String, text: String, time: Long, category: Boolean = false): StatusBarNotification {
        val b = Notification.Builder(context, "msg").setSmallIcon(android.R.drawable.ic_dialog_email)
        val n = if (category) {
            b.setCategory(Notification.CATEGORY_MESSAGE).setContentTitle("Alice").setContentText(text).build()
        } else {
            b.setStyle(Notification.MessagingStyle(Person.Builder().setName("Moi").build()).addMessage(text, time, Person.Builder().setName("Alice").build())).build()
        }
        @Suppress("DEPRECATION")
        return StatusBarNotification(pkg, pkg, 1, null, Process.myUid(), 0, 0, n, Process.myUserHandle(), time)
    }

    @Test
    fun postedMessageBecomesContent() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val env = ModuleEnv(context, scope, MutableStateFlow(BulleSettings()))
        val seen = CopyOnWriteArrayList<List<IslandContent>>()
        scope.launch { MessagesModule.contents(env).collect { seen += it } }
        delay(300) // abonnement au flux d'événements

        NotificationRepository.onPosted(sbn("com.chat", "Salut", 1_000L))
        val first = withTimeout(3_000) {
            while (seen.lastOrNull().isNullOrEmpty()) delay(20)
            seen.last()
        }
        val c = first.single() as MessageContent
        assertEquals("Alice", c.displayName)
        assertEquals("Salut", c.compactSubtitle)
        val firstKey = c.key

        // Mise à jour silencieuse (même dernier message) : même contenu, même clé.
        NotificationRepository.onPosted(sbn("com.chat", "Salut", 1_000L))
        delay(200)
        assertEquals(firstKey, seen.last().single().key)

        // Nouveau message : nouvelle clé (l'île l'annonce à nouveau).
        NotificationRepository.onPosted(sbn("com.chat", "Tu viens ?", 2_000L))
        withTimeout(3_000) { while (seen.last().singleOrNull()?.key == firstKey) delay(20) }
        assertEquals("Tu viens ?", seen.last().single().compactSubtitle)

        // Appli exclue : ignorée.
        MessagesPrefs.update(context) { it.copy(excludedApps = setOf("com.excluded")) }
        NotificationRepository.onPosted(sbn("com.excluded", "Pub", 3_000L, category = true))
        delay(200)
        assertTrue(seen.last().none { (it as MessageContent).info.packageName == "com.excluded" })

        // Notification retirée (lue ailleurs) : le contenu part.
        NotificationRepository.onRemoved(sbn("com.chat", "Tu viens ?", 2_000L), 0)
        withTimeout(3_000) { while (seen.last().isNotEmpty()) delay(20) }
        scope.cancel()
    }
}
