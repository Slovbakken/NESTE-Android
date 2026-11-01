package no.neste.coach

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class FocusGuardStoreTest {
    private lateinit var context:Context
    private lateinit var guard:FocusGuardStore
    private val demoPkg="com.example.distraction"

    @Before fun setup() {
        context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("neste_focus_guard_v1",Context.MODE_PRIVATE).edit().clear().commit()
        guard=FocusGuardStore(context)
    }

    private fun exampleSnapshot(measuredDays:Int):ScreenTimeRepository.Snapshot {
        val date=LocalDate.now()
        val data=(6 downTo 0).map { i->
            val measured=i>0 && i<=measuredDays
            val app=ScreenTimeRepository.AppTime(demoPkg,"Eksempelapp",60L*60L*1000L)
            ScreenTimeRepository.DayTime(date.minusDays(i.toLong()).toString(),
                if(measured)app.milliseconds else 0L,
                if(measured)listOf(app) else emptyList(),
                if(measured)"hendelser" else "utilgjengelig")
        }
        return ScreenTimeRepository.Snapshot(true,data,System.currentTimeMillis())
    }

    @Test fun limitsReduceByTenPercentButNeverBelowFifteen(){
        assertEquals(90,FocusGuardStore.limitFor(90,0))
        assertEquals(80,FocusGuardStore.limitFor(90,1))
        assertEquals(15,FocusGuardStore.limitFor(30,40))
    }

    @Test fun unselectedAppsNeverBlock(){
        assertFalse(guard.shouldBlock(demoPkg,400))
        assertFalse(FocusGuardStore.canRestrict("no.neste.coach"))
        assertFalse(FocusGuardStore.canRestrict("com.android.settings"))
        assertFalse(FocusGuardStore.canRestrict("no.bankid.app"))
    }

    @Test fun quotaApprovalRequiresObservationAndFourMeasuredDays(){
        guard.acceptConsent(true)
        guard.startObservation()
        assertFalse(guard.approvePlan(setOf(demoPkg),exampleSnapshot(6)))
        context.getSharedPreferences("neste_focus_guard_v1",Context.MODE_PRIVATE)
            .edit().putLong("observed_since",System.currentTimeMillis()-8*86_400_000L).commit()
        assertFalse(guard.approvePlan(setOf(demoPkg),exampleSnapshot(3)))
        assertTrue(guard.approvePlan(setOf(demoPkg),exampleSnapshot(6)))
        assertEquals(listOf(demoPkg),guard.rules().map{it.pkg})
        assertTrue(guard.enabled())
        assertTrue(guard.shouldBlock(demoPkg,60))
    }

    @Test fun onlyExplicitlySelectedPackagesCountTowardsQuota(){
        guard.acceptConsent(true)
        context.getSharedPreferences("neste_focus_guard_v1",Context.MODE_PRIVATE)
            .edit().putLong("observed_since",System.currentTimeMillis()-8*86_400_000L).commit()
        assertTrue(guard.approvePlan(setOf(demoPkg),exampleSnapshot(6)))
        assertFalse(guard.shouldBlock("com.example.other",1000))
        assertTrue(guard.shouldBlock(demoPkg,61))
        guard.stop()
        assertFalse(guard.shouldBlock(demoPkg,1000))
    }

    @Test fun mildModeCanWarnButNotBlock(){
        guard.acceptConsent(true)
        context.getSharedPreferences("neste_focus_guard_v1",Context.MODE_PRIVATE)
            .edit().putLong("observed_since",System.currentTimeMillis()-8*86_400_000L).commit()
        assertTrue(guard.approvePlan(setOf(demoPkg),exampleSnapshot(6)))
        guard.setMode("mild")
        assertFalse(guard.shouldBlock(demoPkg,1000))
        assertTrue(guard.canMonitor(demoPkg))
    }
}
