package no.neste.coach

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenTimeMathTest {
    private val resumed=UsageEvents.Event.ACTIVITY_RESUMED
    private val paused=UsageEvents.Event.ACTIVITY_PAUSED
    private val screenOff=UsageEvents.Event.SCREEN_NON_INTERACTIVE
    private fun b(time:Long,pkg:String,type:Int)=ScreenTimeMath.Boundary(time,pkg,type)

    @Test fun foregroundAndPauseAccumulateDuration(){
        val result=ScreenTimeMath.activeMillis(
            listOf(b(0,"a.example",resumed),b(120_000,"a.example",paused)),
            0,180_000)
        assertEquals(120_000L,result["a.example"])
    }

    @Test fun midnightBoundaryClipsPriorForeground(){
        val result=ScreenTimeMath.activeMillis(
            listOf(b(-60_000,"a.example",resumed),b(80_000,"a.example",paused)),
            0,200_000)
        assertEquals(80_000L,result["a.example"])
    }

    @Test fun switchingAppsClosesPreviousSession(){
        val result=ScreenTimeMath.activeMillis(
            listOf(b(0,"a",resumed),b(20_000,"b",resumed),b(50_000,"b",paused)),
            0,100_000)
        assertEquals(20_000L,result["a"])
        assertEquals(30_000L,result["b"])
    }

    @Test fun screenOffStopsCounting(){
        val result=ScreenTimeMath.activeMillis(
            listOf(b(0,"a",resumed),b(40_000,"",screenOff),b(90_000,"a",paused)),
            0,120_000)
        assertEquals(40_000L,result["a"])
    }

    @Test fun ongoingSessionClosesAtRequestedEnd(){
        val result=ScreenTimeMath.activeMillis(listOf(b(40_000,"a",resumed)),0,100_000)
        assertEquals(60_000L,result["a"])
    }
}
