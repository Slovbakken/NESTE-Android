package no.neste.coach

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.LocalDate

/**
 * Device-independent Android JVM behavior tests. UI tap/navigation tests still
 * need an emulator or physical-device run before wider distribution.
 */
@RunWith(RobolectricTestRunner::class)
class CoachStoreTest {
    private lateinit var store:CoachStore

    @Before fun clean() {
        val context=RuntimeEnvironment.getApplication()
        listOf("neste_state_native_v1.json","neste_state_native_v1.json.bak").forEach {
            File(context.filesDir,it).delete()
        }
        store=CoachStore(context)
        assertTrue(store.setOnboarded(setOf("family","work")))
    }

    @Test fun overdueTaskReplacesUnstartedPlan() {
        val before=store.currentAction().id
        store.addTask("Lever viktig sak","work",store.today(),true)
        assertTrue(store.currentAction().id.startsWith("task:"))
        val id=store.allTasks().last().getString("id")
        store.completeTask(id)
        assertFalse(store.currentAction().id.startsWith("task:"))
        assertTrue(store.allTasks().last().optBoolean("done"))
    }

    @Test fun checkinPreservesTenPointScale() {
        store.saveCheckin(2,8,4,3,"Mye å gjøre","En rolig pause")
        val log=store.todayLog()
        assertEquals("0-10",log.getString("checkinScale"))
        assertEquals(2,log.getInt("energy10"))
        assertEquals(8,log.getInt("stress10"))
        assertTrue(store.adaptiveHint().isNotEmpty())
        assertTrue(store.snapshot().contains("Mye å gjøre"))
    }

    @Test fun invalidBackupKeepsOriginal() {
        val before=store.snapshot()
        assertFalse(store.importBackup("{not valid json"))
        assertEquals(before,store.snapshot())
        assertTrue(store.onboarded())
    }

    @Test fun firstSuccessfulCardReviewDueTomorrow() {
        store.addCard("Hva betyr NESTE?","Ett lite steg")
        val card=store.dueCards().first()
        store.reviewCard(card.getString("id"),true)
        val saved=store.snapshot()
        assertTrue(saved.contains(LocalDate.parse(store.today()).plusDays(1).toString()))
    }

    @Test fun reflectionUsesStructuredCode() {
        store.setReflection("too_big","Trenger mindre steg")
        assertEquals("too_big",store.todayLog().optString("reasonCode"))
    }
}
