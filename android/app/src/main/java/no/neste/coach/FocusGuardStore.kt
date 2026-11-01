package no.neste.coach

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.roundToInt

/** Private user-approved rules. No Accessibility node text or usage history is stored. */
class FocusGuardStore(private val context:Context) {
    private val prefs=context.getSharedPreferences("neste_focus_guard_v1",Context.MODE_PRIVATE)
    data class Rule(val pkg:String,val baseline:Int,val startedAt:Long,val step:Int=0,val lastReview:Long=0L)
    val modes=listOf("mild","balansert","streng")
    fun mode():String=prefs.getString("mode","balansert")?:"balansert"
    fun setMode(value:String){require(value in modes);prefs.edit().putString("mode",value).apply()}
    fun consent():Boolean=prefs.getBoolean("consent",false)
    fun acceptConsent(accepted:Boolean){prefs.edit().putBoolean("consent",accepted).apply();if(!accepted)stop()}
    fun observedSince():Long=prefs.getLong("observed_since",0L)
    fun startObservation(){if(observedSince()==0L)prefs.edit().putLong("observed_since",System.currentTimeMillis()).apply()}
    fun observedDays():Int=((System.currentTimeMillis()-observedSince()).coerceAtLeast(0L)/86_400_000L).toInt().takeIf{observedSince()>0}?:0
    fun enabled():Boolean=prefs.getBoolean("enabled",false)
    fun stop(){prefs.edit().putBoolean("enabled",false).remove("test_pkg").remove("test_until").apply()}
    fun rules():List<Rule> {
        val list=JSONArray(prefs.getString("rules","[]")?:"[]")
        return (0 until list.length()).mapNotNull{
            val o=list.optJSONObject(it)?:return@mapNotNull null
            val pkg=o.optString("pkg")
            if(!canRestrict(pkg))return@mapNotNull null
            Rule(pkg,o.optInt("baseline").coerceIn(15,720),
                o.optLong("startedAt"),o.optInt("step").coerceIn(0,52),
                o.optLong("lastReview"))
        }
    }
    private fun saveRules(rules:List<Rule>){
        val array=JSONArray()
        rules.forEach{r-> array.put(JSONObject().put("pkg",r.pkg).put("baseline",r.baseline)
            .put("startedAt",r.startedAt).put("step",r.step).put("lastReview",r.lastReview))}
        prefs.edit().putString("rules",array.toString()).apply()
    }
    fun approvePlan(packages:Set<String>,snapshot:ScreenTimeRepository.Snapshot):Boolean {
        // Never calibrate a reduction plan from just an elapsed calendar interval.
        // Require four actual days with accessible Android usage records.
        val measuredDays=snapshot.days.dropLast(1).count{it.source!="utilgjengelig"}
        if(!consent() || observedDays()<7 || !snapshot.hasAccess || measuredDays<4)return false
        val valid=packages.filter{canRestrict(it)}.take(8)
        if(valid.isEmpty())return false
        val now=System.currentTimeMillis()
        val old=rules().associateBy{it.pkg}
        val rules=valid.map{pkg->
            val previous=old[pkg]
            previous?:Rule(pkg,observedAverage(pkg,snapshot).coerceIn(15,720),now,0,now)
        }
        saveRules(rules)
        prefs.edit().putBoolean("enabled",true).remove("test_pkg").remove("test_until").apply()
        return true
    }
    fun currentLimit(rule:Rule):Int=limitFor(rule.baseline,rule.step)
    fun observedAverage(pkg:String,snapshot:ScreenTimeRepository.Snapshot):Int {
        val days=snapshot.days.dropLast(1).filter{it.source!="utilgjengelig"}
        val ms=days.map { day->day.apps.firstOrNull{it.packageName==pkg}?.milliseconds?:0L }
        if(ms.isEmpty())return 30
        return ((ms.average()/60_000.0).roundToInt()/5*5).coerceIn(15,720)
    }
    fun reviewIfDue(snapshot:ScreenTimeRepository.Snapshot) {
        if(!enabled() || !snapshot.hasAccess)return
        val now=System.currentTimeMillis()
        var changed=false
        val next=rules().map{r->
            if(now-r.lastReview<7*86_400_000L)return@map r
            val measured=snapshot.days.dropLast(1).filter{it.source!="utilgjengelig"}
            val passed=measured.count { day ->
                (day.apps.firstOrNull{it.packageName==r.pkg}?.minutes?:0)<=currentLimit(r)+5
            }
            changed=true
            r.copy(step=(r.step+if(measured.size>=4 && passed>=4)1 else 0).coerceAtMost(52),lastReview=now)
        }
        if(changed)saveRules(next)
    }
    fun allowTest(pkg:String) {
        require(consent() && canRestrict(pkg))
        prefs.edit().putString("test_pkg",pkg).putLong("test_until",System.currentTimeMillis()+2*60_000L).apply()
    }
    fun testPkg():String?=prefs.getString("test_pkg",null)?.takeIf{
        System.currentTimeMillis()<prefs.getLong("test_until",0L)
    }
    fun canMonitor(pkg:String):Boolean {
        if(!consent() || !canRestrict(pkg))return false
        return testPkg()==pkg || (enabled() && rules().any{it.pkg==pkg})
    }
    fun canBlock(pkg:String):Boolean {
        if(!consent() || !canRestrict(pkg))return false
        if(testPkg()==pkg)return true
        return enabled() && rules().any{it.pkg==pkg} && mode()!="mild"
    }
    fun isTest(pkg:String):Boolean=testPkg()==pkg
    fun isBypassed(pkg:String):Boolean=System.currentTimeMillis()<prefs.getLong("bypass_until_"+pkg,0L)
    fun bypass(pkg:String,minutes:Int=5) {
        if(!canRestrict(pkg))return
        prefs.edit().putLong("bypass_until_"+pkg,System.currentTimeMillis()+minutes*60_000L).apply()
    }
    fun cooldownRemaining(pkg:String):Long=(prefs.getLong("cooldown_until_"+pkg,0L)-System.currentTimeMillis()).coerceAtLeast(0L)
    fun beginCooldown(pkg:String) {
        if(!canRestrict(pkg))return
        prefs.edit().putLong("cooldown_until_"+pkg,System.currentTimeMillis()+300_000L).apply()
    }
    fun shouldBlock(pkg:String,minutes:Int):Boolean {
        if(!canBlock(pkg) || isBypassed(pkg))return false
        if(isTest(pkg))return true
        val rule=rules().firstOrNull{it.pkg==pkg}?:return false
        return minutes>=currentLimit(rule)
    }
    fun maybeWarn(pkg:String,minutes:Int):Boolean {
        if(!consent() || !enabled())return false
        val rule=rules().firstOrNull{it.pkg==pkg}?:return false
        val limit=currentLimit(rule)
        if(minutes<limit*0.8 || minutes>=limit)return false
        val key="warn_"+LocalDate.now(ZoneId.systemDefault()).toString()+"_"+pkg
        if(prefs.getBoolean(key,false))return false
        prefs.edit().putBoolean(key,true).apply()
        return true
    }
    fun serviceActive():Boolean {
        val am=context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?:return false
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any{it.resolveInfo?.serviceInfo?.packageName==context.packageName
                    && it.resolveInfo?.serviceInfo?.name?.contains("FocusGuardService")==true}
    }
    companion object {
        fun limitFor(baseline:Int,step:Int):Int =
            ((baseline*(0.90).pow(step.coerceIn(0,52))/5.0).roundToInt()*5).coerceAtLeast(15)
        /** Conservative exclusions; only packages the user explicitly selects are eligible. */
        fun canRestrict(pkg:String):Boolean {
            if(pkg.isBlank() || pkg=="no.neste.coach" || pkg=="com.android.settings")return false
            val forbidden=listOf("com.android.systemui","com.android.launcher",
                "com.google.android.gms","com.google.android.gsf",
                "com.google.android.apps.nexuslauncher","com.google.android.dialer",
                "com.android.dialer","com.android.phone","com.google.android.apps.messaging",
                "com.android.mms","com.android.contacts","com.google.android.contacts",
                "com.google.android.apps.authenticator2","no.bankid.app")
            if(pkg in forbidden || pkg.startsWith("com.android.inputmethod")
                || pkg.startsWith("com.google.android.inputmethod")
                || pkg.startsWith("com.android.permissioncontroller")
                || pkg.startsWith("com.google.android.permissioncontroller")
                || pkg.startsWith("com.android.settings.")
                || pkg.startsWith("no.bankid"))return false
            return true
        }
    }
}
