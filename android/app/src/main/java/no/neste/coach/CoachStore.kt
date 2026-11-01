package no.neste.coach

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.min
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** NESTE 1.1 – deterministic, offline coach and lossless V1 JSON-compatible store.
 * All edits retain unrecognised original JSON fields, including learning cards,
 * historical 1–5 ratings and original notification preferences.
 */
class CoachStore(private val context: Context) {
    data class Action(val id: String, val area: String, val title: String, val description: String, val effort: Int, val why: String)
    val areaNames = linkedMapOf("family" to "Familie og hjem", "work" to "Jobb", "training" to "Trening", "learn" to "Læring", "memory" to "Hukommelse", "phone" to "Skjermtid", "recovery" to "Restitusjon")
    val actions: List<Action> = run {
        val arr = JSONArray(context.assets.open("actions.json").bufferedReader().use { it.readText() })
        (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); Action(o.getString("id"), o.getString("area"), o.getString("title"), o.getString("description"), o.getInt("effort"), o.getString("why")) }
    }
    private val file = AtomicFile(File(context.filesDir, "neste_state_native_v1.json"))
    var revision by mutableIntStateOf(0)
        private set
    var migrationMessage: String? = null
        private set
    private var data: JSONObject = readNative() ?: defaults()

    fun isNativeStored(): Boolean = file.baseFile.exists()
    fun onboarded() = data.optBoolean("onboarded", false)
    fun name() = data.optString("name", "")
    fun today(): String = LocalDate.now(ZoneId.of("Europe/Oslo")).toString()
    fun todayLog(): JSONObject = data.optJSONObject("logs")?.optJSONObject(today()) ?: JSONObject()
    fun dayResult(): String = todayLog().optString("result")
    fun isInProgress(): Boolean = data.optJSONObject("plans")?.optJSONObject(today())?.optBoolean("inProgress",false) ?: false
    fun capacity(): String = data.optJSONObject("settings")?.optString("capacity", "normal") ?: "normal"
    fun reducedMotion() = data.optJSONObject("settings")?.optBoolean("reducedMotion", false) ?: false
    fun haptics() = data.optJSONObject("settings")?.optBoolean("haptics", true) ?: true
    fun priority(area: String) = data.optJSONObject("areas")?.optInt(area, 0) ?: 0
    fun allTasks(): List<JSONObject> { val a = data.optJSONArray("tasks") ?: JSONArray(); return (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
    fun reminders(): JSONObject = data.optJSONObject("reminders") ?: JSONObject()
    fun logs(): JSONObject = data.optJSONObject("logs") ?: JSONObject()
    fun snapshot(): String = data.toString(2)
    fun cardsCount(): Int = data.optJSONArray("cards")?.length() ?: 0
    fun isMigrationPending(): Boolean = !isNativeStored()

    private fun readNative(): JSONObject? = try { JSONObject(file.openRead().bufferedReader().use { it.readText() }).also { validate(it) } } catch (_: Exception) { null }
    private fun validate(o: JSONObject) {
        require(o.optInt("schema", -1) == 1) { "Feil format" }
        require(o.optJSONObject("areas") != null && o.optJSONObject("plans") != null && o.optJSONObject("logs") != null) { "Manglende lagringsfelter" }
        require(o.optJSONArray("tasks") != null && o.optJSONArray("cards") != null && o.optJSONObject("reminders") != null) { "Ufullstendige data" }
    }
    private fun defaults(): JSONObject = JSONObject().apply {
        put("schema",1); put("onboarded",false); put("name","")
        put("areas", JSONObject().apply { put("family",3);put("work",3);put("training",3);put("learn",1);put("memory",2);put("phone",2);put("recovery",3) })
        put("plans",JSONObject());put("logs",JSONObject());put("tasks",JSONArray());put("cards",JSONArray())
        put("reminders",JSONObject().apply { put("morning",reminder("08:15",true)); put("home",reminder("17:00",false)); put("evening",reminder("20:45",false)) })
        put("settings",JSONObject().apply { put("capacity","normal");put("reducedMotion",false);put("haptics",true) })
        put("createdAt", java.time.Instant.now().toString())
    }
    private fun reminder(time:String,on:Boolean)=JSONObject().apply {put("time",time);put("on",on)}

    /** Commit all-or-nothing to a private local file. Unknown V1 fields remain intact. */
    private fun write(o: JSONObject) {
        validate(o)
        val stream = file.startWrite()
        try { stream.write(o.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch(e: Exception) { file.failWrite(stream); throw e }
    }
    private inline fun update(block:(JSONObject)->Unit) {
        val next = JSONObject(data.toString()); block(next); write(next); data = next; revision++
    }
    fun importLegacy(raw:String):Boolean {
        return try {
            val parsed=JSONObject(raw);validate(parsed)
            if(isNativeStored())return false
            // Keep an independent pristine copy before any updates.
            File(context.filesDir,"neste_legacy_v1_unmodified.json").writeText(raw)
            write(parsed);data=parsed;revision++
            migrationMessage="Data fra 1.0 er videreført"
            syncReminders()
            true
        } catch(_:Exception) {migrationMessage="Kunne ikke migrere automatisk. Originaldata fra 1.0 er ikke slettet."; false}
    }
    fun initializeFreshIfMissing() { if(!isNativeStored()){write(data);revision++} }
    fun importBackup(raw:String):Boolean=try {
        val o=JSONObject(raw);validate(o)
        // A safety snapshot of current native data, if any.
        if(isNativeStored()) File(context.filesDir,"neste_backup_before_import.json").writeText(data.toString())
        write(o);data=o;revision++;syncReminders();true
    }catch(_:Exception){false}
    fun setOnboarded(areas: Set<String>) { update { s ->
        val obj=s.getJSONObject("areas")
        for(key in areaNames.keys)obj.put(key, if(key in areas)3 else 0)
        if(areas.isEmpty())obj.put("recovery",3)
        s.put("onboarded",true)
        s.getJSONObject("plans").remove(today())
    };ensurePlan();syncReminders() }
    fun setCapacity(v:String){ if(v !in setOf("low","normal","high"))return
        update { s->s.getJSONObject("settings").put("capacity",v) ; if(dayResult().isEmpty())s.getJSONObject("plans").remove(today()) }; ensurePlan()
    }
    fun toggleArea(area:String){if(area !in areaNames)return;update { s->val obj=s.getJSONObject("areas");obj.put(area, if(obj.optInt(area)>0)0 else 3);if(areaNames.keys.none{obj.optInt(it)>0})obj.put("recovery",1);if(dayResult().isEmpty())s.getJSONObject("plans").remove(today())};ensurePlan()}
    fun setSetting(k:String,v:Boolean){ if(k !in setOf("reducedMotion","haptics"))return;update{s->s.getJSONObject("settings").put(k,v)} }
    fun setReminder(kind:String,enabled:Boolean) { if(kind !in setOf("morning","home","evening"))return
        update{s->s.getJSONObject("reminders").optJSONObject(kind)?.put("on",enabled)}; syncReminders()
    }
    fun setReminderTime(kind:String,time:String){if(kind !in setOf("morning","home","evening") || !Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(time))return
        update{s->s.getJSONObject("reminders").optJSONObject(kind)?.put("time",time)};syncReminders()
    }
    fun syncReminders(){val o=reminders();for(kind in listOf("morning","home","evening")){
        val r=o.optJSONObject(kind)?:continue
        val t=r.optString("time", "08:15").split(":"); if(t.size!=2)continue
        ReminderReceiver.setReminder(context,kind,t[0].toIntOrNull()?:8,t[1].toIntOrNull()?:15,r.optBoolean("on"))
    };syncAdvice()}
    private fun syncAdvice(){ val start=LocalDate.now(ZoneId.of("Europe/Oslo"));repeat(7){offset->val d=start.plusDays(offset.toLong()).toString();val a=resolveAction(planFor(d).optString("actionId"));ReminderReceiver.setDailyAdvice(context,d,a.title,a.description) } }

    private fun stableSeed(input:String):Long { var h=2166136261L; for(ch in input){h=(h xor ch.code.toLong())*16777619L and 0xFFFFFFFFL};return h }
    private fun scoreArea(area:String,date:String):Double {
        val priority=priority(area)
        val recent = data.optJSONObject("plans")?:JSONObject()
        val last=(recent.keys().asSequence().filter{it<date && recent.optJSONObject(it)?.optString("area")==area}.maxOrNull())
        val days=if(last==null) 7.0 else min(7,ChronoUnit.DAYS.between(LocalDate.parse(last),LocalDate.parse(date)).toInt()).toDouble()
        val weekday=LocalDate.parse(date).dayOfWeek.value
        val low=capacity()=="low" || todayLog().optInt("energy",5) in 1..2 || todayLog().optInt("sleep",5) in 1..2
        return priority*1.8+days*.9+(stableSeed(date+area)%100)/100.0 + (if(area=="work" && weekday in 1..5)1.5 else 0.0) + (if(area=="family" && weekday in 6..7)1.5 else 0.0) + (if(area=="recovery"&&low)4.0 else 0.0) - (if(area=="training"&&low)1.0 else 0.0) + (if(area=="family")0.35 else 0.0)
    }
    private fun planFor(day:String):JSONObject {
        val existing=data.optJSONObject("plans")?.optJSONObject(day)
        if(existing!=null)return existing
        val due=allTasks().filter{!it.optBoolean("done") && it.optString("due").isNotEmpty() && it.optString("due")<=day}.sortedBy{it.optString("due")}.firstOrNull()
        if(due!=null)return JSONObject().put("area",due.optString("area","family")).put("actionId","task:"+due.getString("id"))
        val area=areaNames.keys.filter{priority(it)>0}.maxByOrNull{scoreArea(it,day)}?:"recovery"
        val low=capacity()=="low" || (todayLog().optInt("energy",5) in 1..2) || (todayLog().optInt("sleep",5) in 1..2)
        val pool=actions.filter{it.area==area && (if(low)it.effort==1 else it.effort>=2)}.ifEmpty{actions.filter{it.area==area}}
        val last=data.optJSONObject("plans")?:JSONObject()
        val recent=last.keys().asSequence().filter{it<day}.sortedDescending().take(5).mapNotNull{last.optJSONObject(it)?.optString("actionId")}.toSet()
        val selected=pool.minWithOrNull(compareBy<Action>({if(it.id in recent)1 else 0},{stableSeed(day+it.id)%1000})) ?: actions.last()
        return JSONObject().put("area",selected.area).put("actionId",selected.id)
    }
    fun ensurePlan(){if(data.optJSONObject("plans")?.has(today())==true)return
        update{s->s.getJSONObject("plans").put(today(),planFor(today()))};syncAdvice()
    }
    private fun resolveAction(id:String):Action {
        if(id.startsWith("task:")){
            val task=allTasks().firstOrNull{it.optString("id")==id.removePrefix("task:")}
            if(task!=null) return Action(id,task.optString("area","family"),"Ta tak i: ${task.optString("title")}","Ta det første konkrete steget i dag. Fem minutter kan være nok.",2,"Du har selv valgt å følge opp denne oppgaven.")
        }
        return actions.firstOrNull{it.id==id}?:actions.last()
    }
    fun currentAction():Action = resolveAction(data.optJSONObject("plans")?.optJSONObject(today())?.optString("actionId") ?: planFor(today()).optString("actionId"))
    fun start(){update { s->val plans=s.getJSONObject("plans"); val p=plans.optJSONObject(today())?:planFor(today());p.put("inProgress",true);plans.put(today(),p) } }
    fun smaller():String {
        val current=currentAction()
        val alternative=actions.filter{it.area==current.area&&it.effort==1&&it.id!=current.id}.minByOrNull{stableSeed(today()+it.id)}
        return if(alternative==null)"Bare første steg: bruk to minutter på å begynne." else {
            update{s->s.getJSONObject("plans").put(today(),JSONObject().put("area",alternative.area).put("actionId",alternative.id).put("adjusted","smaller").put("inProgress",isInProgress()))}
            syncAdvice();alternative.description
        }
    }
    fun switch(){val old=currentAction();val pool=actions.filter{it.area!=old.area&&priority(it.area)>0 && (capacity()!="low"||it.effort==1)}
        if(pool.isEmpty())return
        val a=pool.minByOrNull{stableSeed(today()+it.id+old.id)}?:return
        update{s->s.getJSONObject("plans").put(today(),JSONObject().put("area",a.area).put("actionId",a.id).put("adjusted","switch"))};syncAdvice()
    }
    fun complete(result:String){if(result !in setOf("done","small","skip"))return
        update{s->
            val log=s.getJSONObject("logs").optJSONObject(today())?:JSONObject()
            log.put("result",result).put("at",java.time.Instant.now().toString())
            s.getJSONObject("logs").put(today(),log)
            s.getJSONObject("plans").optJSONObject(today())?.put("inProgress",false)
            val id=s.getJSONObject("plans").optJSONObject(today())?.optString("actionId")?:""
            if(result=="done"&&id.startsWith("task:")){
                val a=s.getJSONArray("tasks");for(i in 0 until a.length()) if(a.optJSONObject(i)?.optString("id")==id.removePrefix("task:")) a.getJSONObject(i).put("done",true)
            }
        }
    }
    fun setReflection(text:String){update{s->val log=s.getJSONObject("logs").optJSONObject(today())?:JSONObject();log.put("reflection",text.take(500));s.getJSONObject("logs").put(today(),log)}}
    fun addTask(title:String,area:String,due:String,owned:Boolean) {if(title.isBlank())return
        update{s->s.getJSONArray("tasks").put(JSONObject().put("id","t"+System.currentTimeMillis()).put("title",title.trim().take(130)).put("area",if(area in areaNames)area else "memory").put("due",due).put("done",false).put("owned",owned).put("createdAt",java.time.Instant.now().toString()))}
    }
    fun completeTask(id:String){update{s->val arr=s.getJSONArray("tasks");for(i in 0 until arr.length()){val o=arr.optJSONObject(i)?:continue;if(o.optString("id")==id){o.put("done",!o.optBoolean("done"));break}}} }
    fun dueCards():List<JSONObject>{val a=data.optJSONArray("cards")?:JSONArray();return (0 until a.length()).mapNotNull{a.optJSONObject(it)}.filter{it.optString("due",today())<=today()} }
    fun addCard(question:String,answer:String){if(question.isBlank()||answer.isBlank())return
        update{s->s.getJSONArray("cards").put(JSONObject().put("id","c"+System.currentTimeMillis()).put("question",question.trim().take(200)).put("answer",answer.trim().take(900)).put("due",today()).put("level",0).put("createdAt",java.time.Instant.now().toString()))}
    }
    fun reviewCard(id:String,remembered:Boolean){update{s->val a=s.getJSONArray("cards");for(i in 0 until a.length()){
        val o=a.optJSONObject(i)?:continue
        if(o.optString("id")==id){val level=if(remembered)min(5,o.optInt("level",0)+1) else 0
            val intervals=listOf(1L,3,7,14,30,60)
            o.put("level",level).put("due",LocalDate.parse(today()).plusDays(intervals[level]).toString()).put("lastReviewed",java.time.Instant.now().toString());break}
    }}}
    fun deleteAllData(){
        // Delete private native data, including pristine migration/import copies.
        for(name in listOf("neste_legacy_v1_unmodified.json","neste_backup_before_import.json"))File(context.filesDir,name).delete()
        android.webkit.WebStorage.getInstance().deleteAllData()
        context.getSharedPreferences("neste_reminders",Context.MODE_PRIVATE).edit().clear().commit()
        data=defaults();write(data);revision++;syncReminders()
    }
    fun weekly(date:LocalDate=LocalDate.now(ZoneId.of("Europe/Oslo"))): List<Pair<String,String?>> = (6 downTo 0).map{off->
        val d=date.minusDays(off.toLong()).toString(); d to logs().optJSONObject(d)?.optString("result")?.takeIf{it.isNotEmpty()}
    }
}
