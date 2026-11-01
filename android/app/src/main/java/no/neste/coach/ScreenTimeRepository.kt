package no.neste.coach

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/**
 * Local Android usage-access bridge. This is not a Digital Wellbeing private API.
 * No app usage data is uploaded or persisted. Only an optional user-selected
 * target (in CoachStore) is stored. Android/OEM estimates may differ.
 */
class ScreenTimeRepository(private val context: Context) {
    data class AppTime(val packageName:String,val displayName:String,val milliseconds:Long) {
        val minutes:Int get() = (milliseconds/60_000L).toInt()
    }
    data class DayTime(val day:String,val totalMillis:Long,val apps:List<AppTime>,val source:String) {
        val minutes:Int get() = (totalMillis/60_000L).toInt()
    }
    data class Snapshot(val hasAccess:Boolean,val days:List<DayTime>,val readAtMillis:Long) {
        val today:DayTime? get() = days.lastOrNull()
    }

    fun hasAccess():Boolean {
        val appOps=context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?:return false
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun read():Snapshot {
        val now=System.currentTimeMillis()
        if(!hasAccess()) return Snapshot(false,emptyList(),now)
        val usage=context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?:return Snapshot(true,emptyList(),now)
        val zone=ZoneId.systemDefault()
        val today=LocalDate.now(zone)
        val days=(6 downTo 0).mapNotNull { off ->
            val date=today.minusDays(off.toLong())
            val begin=date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end=minOf(now,date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            if(begin>=end) null else loadDay(usage,date,begin,end)
        }
        return Snapshot(true,days,now)
    }

    private fun loadDay(manager:UsageStatsManager,date:LocalDate,begin:Long,end:Long):DayTime? {
        try {
            // A few hours before midnight helps include sessions crossing day boundary.
            val queryBegin=(begin-6*60*60*1000L).coerceAtLeast(0L)
            val result=manager.queryEvents(queryBegin,end)
            if(result!=null) {
                val records=ArrayList<ScreenTimeMath.Boundary>()
                val e=UsageEvents.Event()
                while(result.hasNextEvent()){
                    result.getNextEvent(e)
                    val relevant=when(e.eventType){
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED,
                        UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                        UsageEvents.Event.KEYGUARD_SHOWN,
                        UsageEvents.Event.DEVICE_SHUTDOWN -> true
                        else -> false
                    }
                    if(relevant) records.add(ScreenTimeMath.Boundary(e.timeStamp,e.packageName?:"",e.eventType))
                }
                if(records.isNotEmpty()){
                    val millis=ScreenTimeMath.activeMillis(records,begin,end)
                        .filterKeys { validApp(it) }
                    return asDay(date,millis,"hendelser")
                }
            }
        } catch(e:Exception) {
            android.util.Log.w("NESTE","Usage events unavailable, trying aggregate",e)
        }
        // Some OEMs retain events for fewer days; daily aggregate is a fallback
        // and can differ from Digital Wellbeing due to interval expansion.
        return try {
            val all=manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,begin,end)
                ?:return null
            if(all.isEmpty())return null
            val millis=all.filter { validApp(it.packageName) }
                .groupBy {it.packageName}
                .mapValues { (_,items)->items.sumOf { it.totalTimeInForeground.coerceAtLeast(0L) } }
            asDay(date,millis,"aggregat")
        }catch(e:Exception){
            android.util.Log.w("NESTE","Usage aggregates unavailable",e)
            null
        }
    }

    private fun asDay(day:LocalDate,byPackage:Map<String,Long>,source:String):DayTime {
        val apps=byPackage.filterValues {it>=1000L}
            .map { (pkg,ms)->AppTime(pkg,appLabel(pkg),ms) }
            .sortedByDescending {it.milliseconds}
        return DayTime(day.toString(),apps.sumOf{it.milliseconds},apps,source)
    }

    private fun validApp(pkg:String):Boolean {
        if(pkg.isBlank() || pkg==context.packageName || pkg=="com.android.systemui")return false
        val lowercase=pkg.lowercase()
        return !lowercase.contains("launcher") && !lowercase.contains("systemui") &&
            !lowercase.contains("inputmethod") && !lowercase.contains(".keyboard")
    }

    private fun appLabel(pkg:String):String {
        return try {
            @Suppress("DEPRECATION")
            val info=context.packageManager.getApplicationInfo(pkg,0)
            context.packageManager.getApplicationLabel(info).toString().take(42)
        }catch(e:PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.').replaceFirstChar {it.uppercase()}.take(42)
        } catch(e:SecurityException) {
            pkg.substringAfterLast('.').take(42)
        }
    }
}

/**
 * A deliberately simple, testable single-foreground-app estimator.
 * Multi-window, screen recorder overlays and OEM event semantics may differ.
 */
internal object ScreenTimeMath {
    data class Boundary(val timestamp:Long,val packageName:String,val kind:Int)
    fun activeMillis(input:List<Boundary>,start:Long,end:Long):Map<String,Long> {
        require(end>=start)
        if(end==start)return emptyMap()
        val totals=mutableMapOf<String,Long>()
        var current:String?=null
        var since=start
        fun close(at:Long){
            val pkg=current
            if(pkg!=null && pkg.isNotBlank()){
                val duration=(at.coerceIn(start,end)-since.coerceIn(start,end)).coerceAtLeast(0L)
                if(duration>0)totals[pkg]=(totals[pkg]?:0L)+duration
            }
        }
        for(event in input.sortedBy{it.timestamp}){
            if(event.timestamp>end)break
            when(event.kind){
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if(current!=event.packageName){
                        close(event.timestamp)
                        current=event.packageName
                        since=event.timestamp.coerceAtLeast(start)
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> if(current==event.packageName){
                    close(event.timestamp)
                    current=null
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    close(event.timestamp)
                    current=null
                }
            }
        }
        close(end)
        return totals
    }
}
