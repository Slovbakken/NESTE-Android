package no.neste.coach

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Observes only foreground package names. No node text, screenshots or content. */
class FocusGuardService:AccessibilityService() {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var guard:FocusGuardStore
    private lateinit var usage:ScreenTimeRepository
    private var foreground=""
    private var overlay:View?=null
    private var overlayPkg=""
    private var lastCheck=0L
    private val ticker=object:Runnable {
        override fun run() {
            if(foreground.isNotBlank())check(foreground)
            main.postDelayed(this,12_000L)
        }
    }
    override fun onServiceConnected(){
        super.onServiceConnected()
        guard=FocusGuardStore(this)
        usage=ScreenTimeRepository(this)
        val manager=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("focusvern_gentle","Fokusvern",NotificationManager.IMPORTANCE_LOW))
        main.post(ticker)
    }
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(event==null || event.eventType!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return
        val pkg=event.packageName?.toString()?:return
        // Opening an accessibility overlay itself generates WINDOW_STATE_CHANGED
        // events with NESTE's package name. They are NOT app switches: removing
        // the overlay here caused the visible flash/disappear defect.
        val cls=event.className?.toString().orEmpty()
        if(FocusWindowPolicy.isOurOverlayEvent(pkg,packageName,cls))return
        // A real NESTE activity remains an intentional escape hatch.
        if(pkg==packageName){
            foreground=""
            hideOverlay()
            return
        }
        // System UI can emit transient events while the overlay is displayed.
        // Ignore those rather than treating them as foreground app changes.
        if(pkg=="com.android.systemui"){
            if(FocusWindowPolicy.isKeyguardEvent(cls)){
                foreground=""
                hideOverlay()
            }
            return
        }
        if(pkg!=foreground){
            foreground=pkg
            if(pkg!=overlayPkg)hideOverlay()
        }
        if(::guard.isInitialized && guard.canMonitor(pkg))check(pkg,true)
        else hideOverlay()
    }
    override fun onInterrupt(){hideOverlay()}
    override fun onDestroy(){
        main.removeCallbacks(ticker);hideOverlay();worker.shutdownNow()
        super.onDestroy()
    }
    private fun check(pkg:String,force:Boolean=false){
        if(!::guard.isInitialized || !guard.canMonitor(pkg) || guard.isBypassed(pkg)){
            hideOverlay();return
        }
        val now=System.currentTimeMillis()
        if(!force && now-lastCheck<10_000L)return
        lastCheck=now
        if(guard.isTest(pkg)){showOverlay(pkg,0,true);return}
        worker.execute {
            val snap=runCatching{usage.read()}.getOrNull()
            if(snap==null || !snap.hasAccess){main.post{if(foreground==pkg)hideOverlay()};return@execute}
            guard.reviewIfDue(snap)
            val minutes=snap.today?.apps?.firstOrNull{it.packageName==pkg}?.minutes?:0
            val rule=guard.rules().firstOrNull{it.pkg==pkg}?:return@execute
            val limit=guard.currentLimit(rule)
            val blocked=guard.shouldBlock(pkg,minutes)
            val warn=guard.maybeWarn(pkg,minutes)
            main.post {
                if(foreground!=pkg)return@post
                if(warn)showWarning(pkg,limit,minutes)
                if(blocked && !guard.isBypassed(pkg))showOverlay(pkg,limit,false)
                else hideOverlay()
            }
        }
    }
    private fun showWarning(pkg:String,limit:Int,minutes:Int){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val intent=Intent(this,MainActivity::class.java)
        val pending=PendingIntent.getActivity(this,250,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice=Notification.Builder(this,"focusvern_gentle")
            .setSmallIcon(R.drawable.ic_stat_n)
            .setContentTitle("NESTE · Litt skjermtid igjen")
            .setContentText(pkg.substringAfterLast('.')+": "+(limit-minutes)+" minutter igjen.")
            .setContentIntent(pending).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(500+(pkg.hashCode() and Int.MAX_VALUE)%1000,notice)
    }
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply{
        setColor(color);cornerRadius=radius*resources.displayMetrics.density
    }
    private fun createButton(label:String,primary:Boolean,action:()->Unit):Button =
        Button(this).apply{
            text=label;isAllCaps=false;textSize=14f
            setTextColor(if(primary)Color.WHITE else Color.rgb(32,62,50))
            background=rounded(if(primary)Color.rgb(32,62,50) else Color.rgb(239,243,233),14)
            setOnClickListener{action()}
        }
    private fun showOverlay(pkg:String,limit:Int,test:Boolean){
        if(foreground!=pkg || guard.isBypassed(pkg))return
        if(overlay!=null && overlayPkg==pkg)return
        hideOverlay()
        val wm=getSystemService(WINDOW_SERVICE) as WindowManager
        fun d(n:Int)=(n*resources.displayMetrics.density).toInt()
        val column=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(d(25),d(45),d(25),d(35))
            background=rounded(Color.rgb(246,245,240),0)
        }
        fun text(value:String,size:Float):TextView=TextView(this).apply{
            this.text=value;textSize=size;setTextColor(Color.rgb(32,41,36))
            setPadding(0,d(8),0,d(17))
        }
        val view=ScrollView(this).apply{isFillViewport=true;addView(column)}
        column.addView(text("NESTE / FOKUSVERN",12f))
        column.addView(View(this),LinearLayout.LayoutParams(1,d(40)))
        column.addView(text(if(test)"Test av sperren" else "Appen tar pause.",30f))
        column.addView(text(
            if(test) "Slik ser det ut når NESTE avbryter en app. Du kan når som helst gå tilbake."
            else "Du har nådd din egen grense på "+limit+" minutter for "+pkg.substringAfterLast('.')+". Resten av telefonen fungerer.",16f))
        column.addView(View(this),LinearLayout.LayoutParams(1,d(24)))
        val cooldown=guard.cooldownRemaining(pkg)
        column.addView(text(if(cooldown>0) "Avkjøling: "+((cooldown+59_999L)/60_000L)+" minutter igjen."
            else "Du bestemmer selv om du trenger mer tid.",14f))
        column.addView(createButton("Tilbake til telefonen",true){
            hideOverlay();performGlobalAction(GLOBAL_ACTION_HOME)
        },LinearLayout.LayoutParams(-1,d(54)))
        column.addView(View(this),LinearLayout.LayoutParams(1,d(11)))
        if(!test && cooldown==0L && guard.mode()=="balansert"){
            column.addView(createButton("Start fem minutters pause",false){
                guard.beginCooldown(pkg);hideOverlay();performGlobalAction(GLOBAL_ACTION_HOME)
            },LinearLayout.LayoutParams(-1,d(51)))
            column.addView(View(this),LinearLayout.LayoutParams(1,d(11)))
        }
        if(cooldown==0L || test){
            column.addView(createButton("Jeg trenger fem minutter nå",false){
                guard.bypass(pkg,5);hideOverlay()
            },LinearLayout.LayoutParams(-1,d(51)))
        }
        column.addView(View(this),LinearLayout.LayoutParams(1,d(11)))
        column.addView(createButton("Endre eller slå av Fokusvern",false){
            hideOverlay();performGlobalAction(GLOBAL_ACTION_HOME)
            startActivity(Intent(this,FocusGuardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        },LinearLayout.LayoutParams(-1,d(51)))
        val params=WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)
        params.gravity=Gravity.TOP or Gravity.START
        try{wm.addView(view,params);overlay=view;overlayPkg=pkg}
        catch(e:Exception){
            android.util.Log.w("NESTE","Overlay not permitted on this device",e)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }
    private fun hideOverlay(){
        val old=overlay;overlay=null;overlayPkg=""
        if(old!=null)runCatching{(getSystemService(WINDOW_SERVICE) as WindowManager).removeViewImmediate(old)}
    }
}


/** Prevent self-generated accessibility window events from dismissing the guard. */
internal object FocusWindowPolicy {
    fun isOurOverlayEvent(eventPackage:String,ownPackage:String,eventClass:String):Boolean {
        if(eventPackage!=ownPackage)return false
        return !eventClass.contains("MainActivity") && !eventClass.contains("FocusGuardActivity")
    }
    fun isKeyguardEvent(eventClass:String):Boolean =
        eventClass.contains("Keyguard",ignoreCase=true) ||
        eventClass.contains("Lockscreen",ignoreCase=true)
}
