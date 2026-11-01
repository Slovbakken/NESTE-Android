@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package no.neste.coach

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.os.Process
import androidx.compose.runtime.mutableIntStateOf
import java.time.ZoneId
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import org.json.JSONTokener
import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import android.widget.Toast

private val Paper=Color(0xFFF6F5F0)
private val White=Color(0xFFFFFFFF)
private val Ink=Color(0xFF202924)
private val Muted=Color(0xFF66736B)
private val Forest=Color(0xFF213D32)
private val Sage=Color(0xFFE5EBE4)
private val Line=Color(0xFFDCE1D8)

class MainActivity : ComponentActivity() {
    private lateinit var coach:CoachStore
    private var migrationLoading by mutableStateOf(true)
    private var screenTimeMinutes by mutableIntStateOf(-1)
    private var migrationIssue by mutableStateOf<String?>(null)
    private val requestNotification = registerForActivityResult(ActivityResultContracts.RequestPermission()){ granted ->
        if(!granted) { /* Settings shows that notifications need system permission. */ }
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        uri?.let{contentResolver.openOutputStream(it)?.use{o->o.write(coach.snapshot().toByteArray(Charsets.UTF_8))}}
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)try {
            val content=contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:return@registerForActivityResult
            val success=coach.importBackup(content)
            Toast.makeText(this,if(success)"Sikkerhetskopien er importert" else "Import mislyktes. Eksisterende data er beholdt.",Toast.LENGTH_LONG).show()
            if(success && migrationIssue!=null) migrationIssue=null
        }catch(e:Exception){Toast.makeText(this,"Kunne ikke lese filen. Ingen data er slettet.",Toast.LENGTH_LONG).show()}
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=androidx.activity.SystemBarStyle.light(android.graphics.Color.rgb(246,245,240),android.graphics.Color.rgb(246,245,240)),navigationBarStyle=androidx.activity.SystemBarStyle.light(android.graphics.Color.rgb(246,245,240),android.graphics.Color.rgb(246,245,240)))
        coach=CoachStore(this)
        ReminderReceiver.createChannel(this)
        // Critical: the hidden, temporary compatibility WebView is never the actual UI.
        // The exact old URL preserves the V1 file origin for localStorage reads.
        if(coach.hasDamagedNativeData()){
            migrationIssue="Lagrede data kan ikke leses. Importer en sikkerhetskopi eller start på nytt. Originalfilen beholdes."
            migrationLoading=false
        } else if(coach.isMigrationPending()) migrateLegacyWebStorage()
        else {runCatching{coach.syncReminders()};migrationLoading=false}
        setContent {
            val currentRevision=coach.revision
            @Suppress("UNUSED_VARIABLE") val recompose=currentRevision
            NesteTheme { if(migrationLoading) {
                Box(Modifier.fillMaxSize().background(Paper),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Forest)}
            } else if(migrationIssue!=null) {
                Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp),
                    verticalArrangement=Arrangement.spacedBy(18.dp)){
                    Spacer(Modifier.height(45.dp))
                    Text("Dine data først.",style=MaterialTheme.typography.headlineLarge)
                    Text(migrationIssue?:"",color=Muted,lineHeight=24.sp)
                    SolidButton("Importer sikkerhetskopi",onClick={importLauncher.launch(arrayOf("application/json","text/plain"))},modifier=Modifier.fillMaxWidth())
                    SoftButton("Prøv å hente gammel lagring igjen",onClick={
                        if(!coach.hasDamagedNativeData()){migrationLoading=true;migrationIssue=null;migrateLegacyWebStorage()}
                    },modifier=Modifier.fillMaxWidth())
                    SoftButton("Start med et nytt oppsett",onClick={
                        val restored=if(coach.hasDamagedNativeData())coach.recoverDamagedNativeData() else runCatching{coach.initializeFreshIfMissing();true}.getOrDefault(false)
                        if(restored)migrationIssue=null
                        else Toast.makeText(this@MainActivity,"Kunne ikke sikre tidligere data",Toast.LENGTH_LONG).show()
                    },modifier=Modifier.fillMaxWidth())
                    Text("Ingenting slettes automatisk. En ny start krever et aktivt valg.",fontSize=13.sp,color=Muted)
                }
            } else NesteApp(coach,
                screenTimeMinutes=screenTimeMinutes,
                onExport={exportLauncher.launch("neste-backup-${coach.today()}.json")},
                onImport={importLauncher.launch(arrayOf("application/json","text/plain"))},
                onPermission={askNotificationPermission()},
                onScreenTime={startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))}
            ) }
        }
    }
    private fun askNotificationPermission(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        // Already granted: do not unexpectedly open Android settings.
    }
    override fun onResume(){super.onResume();if(::coach.isInitialized)screenTimeMinutes=readScreenMinutes()}
    private fun readScreenMinutes():Int{
        try {
            val ops=getSystemService(APP_OPS_SERVICE) as? AppOpsManager ?:return -1
            if(ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),packageName)!=AppOpsManager.MODE_ALLOWED)return -1
            val manager=getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?:return -1
            val today=LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val map=manager.queryAndAggregateUsageStats(today,System.currentTimeMillis())
            val duration=map.filterKeys{it!=packageName&&!it.contains("launcher")&&it!="com.android.systemui"}.values.sumOf{it.totalTimeInForeground.coerceAtLeast(0L)}
            return (duration/60000).coerceIn(0L,1440L).toInt()
        }catch(_:Exception){return -1}
    }
    private fun migrateLegacyWebStorage(){
        val w=WebView(this)
        w.alpha=0f
        (window.decorView as ViewGroup).addView(w,ViewGroup.LayoutParams(1,1))
        w.settings.javaScriptEnabled=true
        w.settings.domStorageEnabled=true
        w.settings.allowFileAccess=true
        w.settings.allowFileAccessFromFileURLs=false
        w.settings.allowUniversalAccessFromFileURLs=false
        w.webViewClient=object:WebViewClient(){
            override fun onPageFinished(view:WebView?, url:String?){
                if(url!="file:///android_asset/index.html")return
                w.evaluateJavascript("(function(){try{return localStorage.getItem('neste_state_v1')||''}catch(e){return ''}})()") {result->
                    try {
                        val legacy=JSONTokener(result).nextValue() as? String
                        if(!legacy.isNullOrBlank()){
                            if(!coach.importLegacy(legacy))migrationIssue="Automatisk import mislyktes. Originaldata er ikke slettet."
                        }else migrationIssue="Fant ingen eldre data i appens lager. Du kan starte nytt eller importere en sikkerhetskopi."
                    }catch(e:Exception){migrationIssue="Kunne ikke undersøke gammel lagring. Originaldata er beholdt."}
                    migrationLoading=false
                    (w.parent as? ViewGroup)?.removeView(w)
                    w.destroy()
                }
            }
        }
        // Invisible to the user. In V1 the same asset origin hosts the live app.
        w.loadUrl("file:///android_asset/index.html")
        // If legacy WebView never finishes loading, do not hold users on a spinner.
        // The original WebView data remains untouched for manual JSON recovery.
        Handler(Looper.getMainLooper()).postDelayed({if(migrationLoading){migrationIssue="Migrering brukte for lang tid. Tidligere data er ikke slettet.";migrationLoading=false;(w.parent as? ViewGroup)?.removeView(w);w.destroy()}},8000L)
    }
}

@Composable private fun NesteTheme(content:@Composable ()->Unit){
    val scheme=lightColorScheme(primary=Forest,onPrimary=White,background=Paper,onBackground=Ink,surface=White,onSurface=Ink,surfaceVariant=Sage,onSurfaceVariant=Muted,outline=Line,secondary=Muted)
    MaterialTheme(colorScheme=scheme,typography=Typography(
        headlineLarge=androidx.compose.ui.text.TextStyle(fontSize=34.sp,lineHeight=39.sp,fontWeight=FontWeight.SemiBold),
        headlineMedium=androidx.compose.ui.text.TextStyle(fontSize=28.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold),
        titleLarge=androidx.compose.ui.text.TextStyle(fontSize=21.sp,lineHeight=28.sp,fontWeight=FontWeight.Medium),
        bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=23.sp),
        bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=20.sp)),content=content)
}

private enum class Page { TODAY, PLAN, PROGRESS, ME }
private enum class Sheet { CAPTURE, CAPACITY, WHY, SMALLER, SWITCH, REFLECT, NOTIFICATION, NEW_CARD, REVIEW_CARD, CHECKIN }

@Composable private fun NesteApp(store:CoachStore,screenTimeMinutes:Int,onExport:()->Unit,onImport:()->Unit,onPermission:()->Unit,onScreenTime:()->Unit){
    var page by rememberSaveable { mutableStateOf(Page.TODAY) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var focus by rememberSaveable { mutableStateOf(false) }
    var smallerOverride by remember { mutableStateOf("") }
    // An explicit, observable navigation state: persistence alone must not decide
    // whether the onboarding screen disappears after pressing the final button.
    var onboardingFinished by remember { mutableStateOf(store.onboarded()) }
    val motion=!store.reducedMotion()
    LaunchedEffect(store.revision) { if(!store.onboarded()) onboardingFinished=false }
    BackHandler(enabled=focus){focus=false}
    BackHandler(enabled=!focus && page!=Page.TODAY && sheet==null){page=Page.TODAY}
    if(!onboardingFinished){
        Onboarding(
            store=store,
            onNotifications={ if(Build.VERSION.SDK_INT>=33)onPermission() },
            onFinished={ onboardingFinished=true }
        )
        return
    }
    val showFocus: @Composable () -> Unit = {
        FocusScreen(store,smallerOverride,onSmaller={sheet=Sheet.SMALLER},onDone={r->store.complete(r);focus=false;smallerOverride=""},onBack={focus=false})
    }
    val showNavigation: @Composable () -> Unit = {
        Scaffold(containerColor=Paper,bottomBar={ BottomNav(page,onSelect={page=it},onCapture={sheet=Sheet.CAPTURE}) }) {insets->
            Box(Modifier.fillMaxSize().padding(insets)) {
                if(motion) AnimatedContent(targetState=page,label="tabs",transitionSpec={
                    (fadeIn(tween(190))+slideInHorizontally(tween(190)){it/16}) togetherWith (fadeOut(tween(130))+slideOutHorizontally(tween(130)){-it/18})
                }){p->PageBody(p,store,onSheet={sheet=it},onStart={store.start();focus=true},onExport,onImport,onPermission,onScreenTime,screenTimeMinutes)}
                else PageBody(page,store,onSheet={sheet=it},onStart={store.start();focus=true},onExport,onImport,onPermission,onScreenTime,screenTimeMinutes)
            }
        }
    }
    if(motion) AnimatedContent(targetState=focus,label="focus-mode",transitionSpec={
        if(targetState) {
            (fadeIn(tween(220))+slideInVertically(tween(260)){it/9}) togetherWith (fadeOut(tween(155))+slideOutVertically(tween(170)){-it/12})
        }else{
            (fadeIn(tween(180))+slideInVertically(tween(220)){-it/11}) togetherWith (fadeOut(tween(170))+slideOutVertically(tween(190)){it/11})
        }
    }){showFocused->if(showFocused)showFocus() else showNavigation()}
    else if(focus) showFocus() else showNavigation()
    if(sheet!=null) {
        val current=sheet!!
        ModalBottomSheet(onDismissRequest={sheet=null},containerColor=White,dragHandle={ BottomSheetDefaults.DragHandle(color=Line) },shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp)){
            Box(Modifier.navigationBarsPadding().imePadding().fillMaxWidth().heightIn(max=580.dp).verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp)){
                when(current){
                    Sheet.CAPTURE -> CaptureSheet(store){sheet=null}
                    Sheet.CAPACITY -> CapacitySheet(store){sheet=null}
                    Sheet.WHY -> WhySheet(store){sheet=null}
                    Sheet.SMALLER -> SmallerSheet(onChoose={kind->
                        if(kind=="same")smallerOverride="Bare første steg: bruk to minutter på å begynne."
                        else{smallerOverride="";store.smaller()}
                        sheet=null
                    })
                    Sheet.SWITCH -> SwitchSheet(store,onConfirm={store.switch();sheet=null},onClose={sheet=null})
                    Sheet.REFLECT -> ReflectionSheet(store){sheet=null}
                    Sheet.NOTIFICATION -> ReminderSheet(store,onPermission){sheet=null}
                    Sheet.NEW_CARD -> NewCardSheet(store){sheet=null}
                    Sheet.REVIEW_CARD -> ReviewCardSheet(store){sheet=null}
                    Sheet.CHECKIN -> CheckinSheet(store){sheet=null}
                }
            }
        }
    }
}

@Composable private fun PageBody(page:Page,store:CoachStore,onSheet:(Sheet)->Unit,onStart:()->Unit,onExport:()->Unit,onImport:()->Unit,onPermission:()->Unit,onScreenTime:()->Unit,screenTimeMinutes:Int){
    when(page){
        Page.TODAY->TodayScreen(store,onSheet,onStart)
        Page.PLAN->PlanScreen(store,onSheet)
        Page.PROGRESS->ProgressScreen(store,onSheet)
        Page.ME->MeScreen(store,onSheet,onExport,onImport,onPermission,onScreenTime,screenTimeMinutes)
    }
}

@Composable private fun BottomNav(selected:Page,onSelect:(Page)->Unit,onCapture:()->Unit){
    Surface(shadowElevation=10.dp,color=White){
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(76.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceAround){
            NavItem(Icons.Default.Home,"I dag",selected==Page.TODAY){onSelect(Page.TODAY)}
            NavItem(Icons.Default.CalendarMonth,"Plan",selected==Page.PLAN){onSelect(Page.PLAN)}
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.clickable(onClick=onCapture)){
                Box(Modifier.size(48.dp).background(Forest,RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Add,"Fang opp",tint=White,modifier=Modifier.size(25.dp))}
                Text("Fang opp",fontSize=10.sp,color=Forest,fontWeight=FontWeight.SemiBold)
            }
            NavItem(Icons.Default.ShowChart,"Utvikling",selected==Page.PROGRESS){onSelect(Page.PROGRESS)}
            NavItem(Icons.Default.PersonOutline,"Meg",selected==Page.ME){onSelect(Page.ME)}
        }
    }
}
@Composable private fun NavItem(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,selected:Boolean,onClick:()->Unit){
    Column(Modifier.widthIn(min=49.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick=onClick).padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
        Box(Modifier.size(width=46.dp,height=28.dp).background(if(selected)Sage else Color.Transparent,CircleShape),contentAlignment=Alignment.Center){Icon(icon,contentDescription=null,tint=if(selected)Forest else Muted,modifier=Modifier.size(21.dp))}
        Text(label,fontSize=10.sp,color=if(selected)Forest else Muted,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,maxLines=1)
    }
}

@Composable private fun Intro(title:String,eyebrow:String?=null){
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        if(eyebrow!=null) Text(eyebrow.uppercase(Locale("nb","NO")),color=Muted,fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.3.sp)
        Text(title,style=MaterialTheme.typography.headlineMedium,color=Ink)
    }
}
@Composable private fun MiniLabel(text:String){Text(text.uppercase(Locale("nb","NO")),fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.sp,color=Muted)}
@Composable private fun SolidButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true){
    Button(onClick=onClick,modifier=modifier.heightIn(min=54.dp),enabled=enabled,colors=ButtonDefaults.buttonColors(containerColor=Forest,contentColor=White),shape=RoundedCornerShape(17.dp),contentPadding=PaddingValues(horizontal=20.dp)) {Text(label,fontSize=15.sp,fontWeight=FontWeight.SemiBold)}
}
@Composable private fun SoftButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier){
    TextButton(onClick=onClick,modifier=modifier.heightIn(min=48.dp),colors=ButtonDefaults.textButtonColors(contentColor=Forest)){Text(label,fontSize=14.sp,fontWeight=FontWeight.Medium)}
}
@Composable private fun Chip(label:String,active:Boolean,onClick:()->Unit){
    Surface(shape=RoundedCornerShape(14.dp),color=if(active)Forest else Sage,onClick=onClick){Text(label,Modifier.padding(horizontal=15.dp,vertical=11.dp),color=if(active)White else Forest,fontSize=13.sp,fontWeight=FontWeight.Medium)}
}

@Composable private fun TodayScreen(store:CoachStore,onSheet:(Sheet)->Unit,onStart:()->Unit){
    val a=store.currentAction(); val result=store.dayResult();val cap=store.capacity()
    val hour=LocalTime.now().hour
    val greeting=when{hour<11->"God morgen";hour<17->"God ettermiddag";else->"God kveld"}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(top=30.dp,bottom=26.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Column {
                MiniLabel(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d. MMMM",Locale("nb","NO"))))
                Spacer(Modifier.height(10.dp));Text(greeting+if(store.name().isNotBlank())", ${store.name()}" else "",style=MaterialTheme.typography.headlineMedium)
            }
            Box(Modifier.size(46.dp).background(Sage,CircleShape),contentAlignment=Alignment.Center){Text("N",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Forest)}
        }
        Spacer(Modifier.height(22.dp))
        Surface(shape=RoundedCornerShape(14.dp),color=Sage,onClick={onSheet(Sheet.CAPACITY)}){
            Row(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.BatteryChargingFull,null,tint=Forest,modifier=Modifier.size(18.dp));Spacer(Modifier.width(7.dp));Text("Kapasitet: "+when(cap){"low"->"Lite";"high"->"Mye";else->"Normal"},fontSize=13.sp,color=Forest,fontWeight=FontWeight.Medium);Spacer(Modifier.width(10.dp));Icon(Icons.Default.ExpandMore,null,tint=Forest,modifier=Modifier.size(17.dp))}
        }
        Spacer(Modifier.height(12.dp))
        SoftButton(if(store.todayLog().has("energy10"))"Oppdater dagens innsjekk" else "Dagens innsjekk · 30 sek",onClick={onSheet(Sheet.CHECKIN)})
        val hint=store.adaptiveHint()
        if(hint.isNotBlank()) Text(hint,color=Muted,fontSize=13.sp,lineHeight=18.sp)
        Spacer(Modifier.height(16.dp))
        MiniLabel(if(result.isEmpty())"Det viktigste i dag" else "Dagens steg")
        Spacer(Modifier.height(13.dp))
        if(result.isNotEmpty()){
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.CheckCircle,null,tint=Forest,modifier=Modifier.size(30.dp));Spacer(Modifier.width(12.dp));Text(when(result){"done"->"Det viktigste er gjort.";"small"->"Et lite steg er gjort.";else->"Du valgte å stå over i dag."},fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold)}
            Spacer(Modifier.height(12.dp));Text("Ingen ekstra krav. I morgen starter vi på nytt.",color=Muted,fontSize=15.sp)
            Spacer(Modifier.height(24.dp));SoftButton("Kort refleksjon",onClick={onSheet(Sheet.REFLECT)})
        }else{
            Surface(color=Forest,shape=RoundedCornerShape(26.dp),modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(24.dp)){
                    Text(store.areaNames[a.area]?:a.area,color=Color(0xFFC6D7C7),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    Text(a.title,color=White,fontSize=29.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    Text(a.description,color=Color(0xFFE8EFE9),fontSize=15.sp,lineHeight=22.sp)
                    Spacer(Modifier.height(28.dp))
                    Button(onClick=onStart,colors=ButtonDefaults.buttonColors(containerColor=White,contentColor=Forest),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().height(54.dp)) {Icon(if(store.isInProgress())Icons.Default.PlayArrow else Icons.Default.ArrowForward,null);Spacer(Modifier.width(9.dp));Text(if(store.isInProgress())"Fortsett" else "Start",fontWeight=FontWeight.SemiBold)}
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){SoftButton("Gjør mindre",onClick={onSheet(Sheet.SMALLER)});SoftButton("Bytt forslag",onClick={onSheet(Sheet.SWITCH)})}
            Spacer(Modifier.height(25.dp))
            HorizontalDivider(color=Line)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                Column {MiniLabel("Hvorfor dette?");Spacer(Modifier.height(5.dp));Text("En anbefaling tilpasset fokusområdene dine",fontSize=13.sp,color=Muted)}
                IconButton(onClick={onSheet(Sheet.WHY)}){Icon(Icons.Default.ArrowForward,"Les begrunnelse",tint=Forest)}
            }
        }
    }
}

@Composable private fun FocusScreen(store:CoachStore,smallText:String,onSmaller:()->Unit,onDone:(String)->Unit,onBack:()->Unit){
    val a=store.currentAction();val haptic=LocalHapticFeedback.current
    var seconds by rememberSaveable {mutableIntStateOf(300)}
    var running by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(running){while(running && seconds>0){delay(1000);seconds--};if(seconds<=0)running=false}
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(24.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Tilbake")};Spacer(Modifier.width(5.dp));MiniLabel("Ditt neste steg")}
        Spacer(Modifier.weight(.6f))
        Text(store.areaNames[a.area]?:"",color=Muted,fontSize=13.sp)
        Spacer(Modifier.height(12.dp))
        Text(a.title,style=MaterialTheme.typography.headlineLarge,color=Ink)
        Spacer(Modifier.height(18.dp))
        Text(if(smallText.isNotBlank())smallText else a.description,fontSize=18.sp,lineHeight=27.sp,color=Muted,modifier=Modifier.animateContentSize())
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment=Alignment.CenterVertically){
            Surface(color=Sage,shape=RoundedCornerShape(19.dp)){
                Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){
                    Text("%02d:%02d".format(seconds/60,seconds%60),fontSize=21.sp,color=Forest,fontWeight=FontWeight.Medium)
                    Spacer(Modifier.width(10.dp));IconButton(onClick={running=!running},modifier=Modifier.size(32.dp)){Icon(if(running)Icons.Default.Pause else Icons.Default.PlayArrow,if(running)"Pause" else "Start timer",tint=Forest)}
                }
            }
            Spacer(Modifier.width(10.dp));Text("Valgfri 5-minutters timer",color=Muted,fontSize=12.sp)
        }
        Spacer(Modifier.weight(1f))
        SoftButton("Gjør mindre",onSmaller,Modifier.fillMaxWidth())
        SolidButton("Gjort",onClick={if(store.haptics())haptic.performHapticFeedback(HapticFeedbackType.LongPress);onDone("done")},modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){SoftButton("Delvis gjort",onClick={onDone("small")});SoftButton("Ikke i dag",onClick={onDone("skip")})}
    }
}

@Composable private fun PlanScreen(store:CoachStore,onSheet:(Sheet)->Unit){
    var selected by rememberSaveable{mutableIntStateOf(0)}
    val today=LocalDate.now(ZoneId.of("Europe/Oslo")).toString()
    val soon=LocalDate.now(ZoneId.of("Europe/Oslo")).plusDays(7).toString()
    val tasks=store.allTasks().filter{obj->
        val d=obj.optString("due")
        when(selected){
            0-> !obj.optBoolean("done")&&d.isNotEmpty()&&d<=today
            1-> !obj.optBoolean("done")&&d.isNotEmpty()&&d>today&&d<=soon
            2-> !obj.optBoolean("done")&&(d.isEmpty()||d>soon)
            else->obj.optBoolean("done")
        }
    }
    Column(Modifier.fillMaxSize()){
        Column(Modifier.padding(horizontal=24.dp).padding(top=30.dp)){
            Intro("Det du har foran deg","Plan")
            Spacer(Modifier.height(10.dp));Text("Fang opp det som ellers ville blitt glemt.",color=Muted,fontSize=15.sp)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("I dag","Snart","Senere","Fullført").forEachIndexed{index,title->Chip(title,selected==index){selected=index}}}
            Spacer(Modifier.height(19.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=14.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("Læringskort",fontSize=15.sp,fontWeight=FontWeight.Medium);Text("${store.dueCards().size} klar til repetisjon",fontSize=12.sp,color=Muted)}
            TextButton(onClick={onSheet(Sheet.NEW_CARD)}){Text("+ Nytt",color=Forest)}
            if(store.dueCards().isNotEmpty()) TextButton(onClick={onSheet(Sheet.REVIEW_CARD)}){Text("Repeter",color=Forest)}
        }
        if(tasks.isEmpty()){
            Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.Start){
                HorizontalDivider(color=Line);Spacer(Modifier.height(28.dp))
                Text(if(selected==0)"Ingen oppgaver med frist i dag." else if(selected==3)"Ingen fullførte oppgaver her ennå." else "Ingenting planlagt her ennå.",fontSize=21.sp,fontWeight=FontWeight.Medium,color=Ink)
                Spacer(Modifier.height(8.dp));Text("Legg til noe du vil ta ansvar for, når det dukker opp.",color=Muted)
                Spacer(Modifier.height(18.dp));SoftButton("+ Fang opp",onClick={onSheet(Sheet.CAPTURE)})
            }
        }else LazyColumn(contentPadding=PaddingValues(horizontal=24.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            items(tasks,key={it.optString("id")}){ task->
                Row(Modifier.fillMaxWidth().background(White,RoundedCornerShape(19.dp)).padding(15.dp),verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick={store.completeTask(task.optString("id"))},modifier=Modifier.size(42.dp)){Icon(if(task.optBoolean("done"))Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,if(task.optBoolean("done"))"Angre fullføring" else "Marker gjort",tint=Forest)}
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)){
                        Text(task.optString("title"),fontWeight=FontWeight.Medium,fontSize=15.sp,color=Ink)
                        Spacer(Modifier.height(5.dp))
                        Text((store.areaNames[task.optString("area")]?:"Husk")+ (if(task.optString("due").isNotEmpty())" · ${task.optString("due")}" else "") + (if(task.optBoolean("owned"))" · Mitt ansvar" else ""),fontSize=12.sp,color=Muted)
                    }
                }
            }
        }
    }
}

@Composable private fun ProgressScreen(store:CoachStore,onSheet:(Sheet)->Unit){
    val days=store.weekly();val done=days.count{it.second=="done"};val small=days.count{it.second=="small"};val skipped=days.count{it.second=="skip"}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(top=30.dp,bottom=30.dp)){
        Intro("Det som faktisk virker","Utvikling")
        Spacer(Modifier.height(12.dp));Text("Siste sju dager · Ikke en prestasjonsmåling",color=Muted,fontSize=14.sp)
        Spacer(Modifier.height(28.dp))
        Surface(color=White,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(22.dp)){
                MiniLabel("Små handlinger")
                Spacer(Modifier.height(10.dp));Row(verticalAlignment=Alignment.Bottom){Text((done+small).toString(),fontSize=46.sp,lineHeight=51.sp,fontWeight=FontWeight.SemiBold,color=Forest);Spacer(Modifier.width(10.dp));Text("registrert denne uken",color=Muted,modifier=Modifier.padding(bottom=10.dp),fontSize=13.sp)}
                Spacer(Modifier.height(23.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){days.forEach{(day,result)->
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        Box(Modifier.size(width=32.dp,height=38.dp).background(when(result){"done"->Forest;"small"->Sage;"skip"->Color(0xFFF0E8E0);else->Paper},RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){
                            if(result=="done")Icon(Icons.Default.Check,null,tint=White,modifier=Modifier.size(17.dp))
                            else if(result=="small")Icon(Icons.Default.Remove,null,tint=Forest,modifier=Modifier.size(17.dp))
                            else if(result=="skip")Icon(Icons.Default.HorizontalRule,null,tint=Muted,modifier=Modifier.size(15.dp))
                            else Text("·",color=Muted)
                        }
                        Spacer(Modifier.height(8.dp));Text(LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EE",Locale("nb","NO"))).take(2),fontSize=11.sp,color=Muted)
                    }
                }}
                Spacer(Modifier.height(19.dp))
                Text("Mørk = gjort   ·   Lys = mindre steg   ·   Prikk = ingen registrering",fontSize=11.sp,color=Muted,lineHeight=16.sp)
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            StatTile("Fullført",done.toString(),Modifier.weight(1f))
            StatTile("Forenklet",small.toString(),Modifier.weight(1f))
            StatTile("Stått over",skipped.toString(),Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        val measured=(0L..6L).mapNotNull { off->
            val day=LocalDate.now(ZoneId.of("Europe/Oslo")).minusDays(off).toString()
            store.logs().optJSONObject(day)?.takeIf{it.optString("checkinScale")=="0-10"}
        }
        if(measured.isNotEmpty()){
            MiniLabel("Innsjekker · 0–10")
            Spacer(Modifier.height(7.dp))
            Text("Energi: "+("%.1f".format(Locale.ROOT,measured.map{it.optInt("energy10")}.average()))+
                "   Søvn: "+("%.1f".format(Locale.ROOT,measured.map{it.optInt("sleep10")}.average()))+
                "   (${measured.size} dager)",fontSize=14.sp,color=Ink)
            Spacer(Modifier.height(7.dp))
            Text("Dette er egenrapporterte observasjoner, ikke en diagnose.",color=Muted,fontSize=12.sp)
            Spacer(Modifier.height(20.dp))
        }
        MiniLabel("Kort refleksjon")
        Spacer(Modifier.height(9.dp))
        Text(if(done+small==0)"Ingen handlinger registrert ennå. Vi trenger ikke tolke det som manglende fremgang." else "Du har registrert ${done+small} handlinger. Legg merke til hvilke situasjoner som gjorde dem mulige.",fontSize=15.sp,color=Ink,lineHeight=23.sp)
        Spacer(Modifier.height(13.dp))
        SoftButton("Hva hjalp i dag?",onClick={onSheet(Sheet.REFLECT)})
    }
}
@Composable private fun StatTile(label:String,value:String,modifier:Modifier=Modifier){
    Column(modifier.background(Sage,RoundedCornerShape(17.dp)).padding(13.dp)){
        Text(value,fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=Forest);Spacer(Modifier.height(4.dp));Text(label,fontSize=12.sp,color=Muted)
    }
}

@Composable private fun MeScreen(store:CoachStore,onSheet:(Sheet)->Unit,onExport:()->Unit,onImport:()->Unit,onPermission:()->Unit,onScreenTime:()->Unit,screenTimeMinutes:Int){
    var confirmDelete by remember {mutableStateOf(false)}
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Slette alle data?")},text={Text("Dette sletter oppgaver, historikk, læringskort og innstillinger på enheten. Eksporter først hvis du vil beholde dem.")},confirmButton={TextButton(onClick={store.deleteAllData();confirmDelete=false}){Text("Slett alt",color=Color(0xFFA04737))}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Avbryt")}})
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(top=30.dp,bottom=32.dp)){
        Intro("Dine rammer","Meg")
        Spacer(Modifier.height(12.dp));Text("Tilpass NESTE til hverdagen din.",fontSize=15.sp,color=Muted)
        Spacer(Modifier.height(29.dp));MiniLabel("Mine fokusområder");Spacer(Modifier.height(11.dp))
        store.areaNames.forEach{(area,label)->
            Row(Modifier.fillMaxWidth().clickable{store.toggleArea(area)}.heightIn(min=53.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=15.sp);Checkbox(store.priority(area)>0,onCheckedChange={store.toggleArea(area)},colors=CheckboxDefaults.colors(checkedColor=Forest))}
            HorizontalDivider(color=Line)
        }
        Spacer(Modifier.height(27.dp));MiniLabel("Varsler");Spacer(Modifier.height(8.dp))
        SoftButton("Endre varsler og tidspunkt",onClick={onSheet(Sheet.NOTIFICATION)})
        Spacer(Modifier.height(25.dp));MiniLabel("Tilgjengelighet")
        SettingRow("Redusert bevegelse",store.reducedMotion()){store.setSetting("reducedMotion",it)}
        SettingRow("Haptisk bekreftelse",store.haptics()){store.setSetting("haptics",it)}
        Spacer(Modifier.height(21.dp));MiniLabel("Personvern og data")
        Spacer(Modifier.height(10.dp))
        Text("Alt lagres lokalt. Ingen konto, analyser eller nettverkstilgang.",fontSize=14.sp,color=Muted,lineHeight=21.sp)
        Spacer(Modifier.height(11.dp));SoftButton("Eksporter JSON-sikkerhetskopi",onExport)
        SoftButton("Importer sikkerhetskopi",onImport)
        Text(if(screenTimeMinutes>=0)"Registrert skjermtid i dag: ${screenTimeMinutes} min (Android-estimat)." else "Skjermtid: tilgang ikke gitt",color=Muted,fontSize=12.sp)
        SoftButton("Valgfri skjermtidstilgang",onScreenTime)
        SoftButton("Slett alle lokale data",onClick={confirmDelete=true})
        Spacer(Modifier.height(12.dp));Text("Lagrede læringskort: ${store.cardsCount()} · Historiske innsjekker beholdes i original skala.",fontSize=12.sp,color=Muted)
        Spacer(Modifier.height(26.dp));Text("NESTE 1.1 · Offline personlig coach",fontSize=12.sp,color=Muted)
    }
}
@Composable private fun SettingRow(label:String,checked:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=54.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=15.sp);Switch(checked=checked,onCheckedChange=onChange,colors=SwitchDefaults.colors(checkedTrackColor=Forest))}
}

@Composable private fun CaptureSheet(store:CoachStore,onClose:()->Unit){
    var title by rememberSaveable{mutableStateOf("")}
    var area by rememberSaveable{mutableStateOf("memory")}
    var due by rememberSaveable{mutableStateOf("")}
    var owned by rememberSaveable{mutableStateOf(false)}
    val keyboard=LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(15.dp)){
        Text("Fang opp",style=MaterialTheme.typography.headlineMedium)
        Text("Hva må du ta tak i?",color=Muted,fontSize=15.sp)
        OutlinedTextField(value=title,onValueChange={title=it.take(130)},modifier=Modifier.fillMaxWidth(),placeholder={Text("Skriv én ting du vil huske …")},singleLine=false,minLines=2,maxLines=3,shape=RoundedCornerShape(18.dp))
        MiniLabel("Type")
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
            listOf("family" to "Hjem","work" to "Jobb","memory" to "Husk","learn" to "Idé").forEach{(id,label)->Chip(label,area==id){area=id}}
        }
        MiniLabel("Når?")
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
            Chip("Ingen dato",due==""){due=""};Chip("I morgen",due==LocalDate.now().plusDays(1).toString()){due=LocalDate.now().plusDays(1).toString()};Chip("Denne uken",due==LocalDate.now().plusDays(5).toString()){due=LocalDate.now().plusDays(5).toString()}
        }
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(owned,onCheckedChange={owned=it},colors=CheckboxDefaults.colors(checkedColor=Forest));Text("Dette tar jeg selv ansvar for",fontSize=14.sp)}
        SolidButton("Lagre i Plan",onClick={store.addTask(title,area,due,owned);keyboard?.hide();onClose()},modifier=Modifier.fillMaxWidth(),enabled=title.isNotBlank())
    }
}
@Composable private fun CapacitySheet(store:CoachStore,onClose:()->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("Hvordan er kapasiteten?",style=MaterialTheme.typography.headlineMedium)
        Text("Det er helt greit å velge lite. Da gjør vi mindre, ikke mer.",color=Muted,fontSize=14.sp)
        listOf("low" to "Lite overskudd","normal" to "Normal kapasitet","high" to "Mye overskudd").forEach{(v,label)->
            Row(Modifier.fillMaxWidth().background(if(store.capacity()==v)Sage else Paper,RoundedCornerShape(15.dp)).clickable {store.setCapacity(v);onClose()}.padding(17.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));if(store.capacity()==v)Icon(Icons.Default.Check,null,tint=Forest)}
        }
    }
}
@Composable private fun WhySheet(store:CoachStore,onClose:()->Unit){Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
    Text("Hvorfor akkurat dette?",style=MaterialTheme.typography.headlineMedium)
    Text(store.currentAction().why,fontSize=16.sp,lineHeight=23.sp)
    Text("Forslaget bygger på fokusområder, oppgaver og kapasiteten du har oppgitt. NESTE leser ikke tankene eller kalenderen din.",fontSize=13.sp,color=Muted)
    SoftButton("Lukk",onClose,Modifier.fillMaxWidth())
}}
@Composable private fun SmallerSheet(onChoose:(String)->Unit){Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
    Text("Vi gjør det enklere",style=MaterialTheme.typography.headlineMedium)
    Text("Et lite steg er nok i dag.",color=Muted)
    SolidButton("Bare første steg · 2 min",onClick={onChoose("same")},modifier=Modifier.fillMaxWidth())
    SoftButton("Bytt til en enklere handling",onClick={onChoose("alternative")},modifier=Modifier.fillMaxWidth())
}}
@Composable private fun SwitchSheet(store:CoachStore,onConfirm:()->Unit,onClose:()->Unit){Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
    Text("Et annet forslag?",style=MaterialTheme.typography.headlineMedium)
    Text("Vi velger fra et annet område du har prioritert, uten å legge til flere krav.",color=Muted)
    SolidButton("Vis et annet steg",onClick=onConfirm,modifier=Modifier.fillMaxWidth())
    SoftButton("Behold dette",onClose,Modifier.fillMaxWidth())
}}
@Composable private fun CheckinSheet(store:CoachStore,onClose:()->Unit) {
    val log=store.todayLog()
    var energy by rememberSaveable{mutableFloatStateOf(log.optInt("energy10",5).toFloat())}
    var stress by rememberSaveable{mutableFloatStateOf(log.optInt("stress10",5).toFloat())}
    var sleep by rememberSaveable{mutableFloatStateOf(log.optInt("sleep10",5).toFloat())}
    var functioning by rememberSaveable{mutableFloatStateOf(log.optInt("functioning10",5).toFloat())}
    var burden by rememberSaveable{mutableStateOf(log.optString("burden"))}
    var recovery by rememberSaveable{mutableStateOf(log.optString("recovery"))}
    Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text("Hvordan har dagen vært?",style=MaterialTheme.typography.headlineMedium)
        Text("Svar bare hvis det er nyttig. Tallene er ikke en prestasjon.",fontSize=14.sp,color=Muted)
        listOf("Energi" to energy,"Stress" to stress,"Søvnkvalitet" to sleep,"Overskudd" to functioning).forEachIndexed { index,pair->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text(pair.first,fontSize=14.sp)
                Text(pair.second.roundToInt().toString()+"/10",color=Forest,fontWeight=FontWeight.Medium)
            }
            Slider(value=pair.second,onValueChange={
                when(index){0->energy=it;1->stress=it;2->sleep=it;else->functioning=it}
            },valueRange=0f..10f,steps=9,colors=SliderDefaults.colors(thumbColor=Forest,activeTrackColor=Forest))
        }
        OutlinedTextField(value=burden,onValueChange={burden=it.take(250)},label={Text("Viktigste belastning (valgfritt)")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
        OutlinedTextField(value=recovery,onValueChange={recovery=it.take(250)},label={Text("Hva hjalp deg å hente deg inn? (valgfritt)")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
        SolidButton("Lagre innsjekk",onClick={
            store.saveCheckin(energy.roundToInt(),stress.roundToInt(),sleep.roundToInt(),functioning.roundToInt(),burden,recovery)
            onClose()
        },modifier=Modifier.fillMaxWidth())
    }
}

@Composable private fun ReflectionSheet(store:CoachStore,onClose:()->Unit){
    var selected by rememberSaveable{mutableStateOf("")}
    var note by rememberSaveable{mutableStateOf("")}
    val choices=listOf("easy" to "Lett å starte","low_energy" to "Lite energi","too_big" to "For stort steg","wrong_time" to "Feil tidspunkt")
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("Hva hjalp i dag?",style=MaterialTheme.typography.headlineMedium)
        Text("Det er også nyttig å vite hva som gjorde ting vanskelig.",color=Muted)
        choices.forEach{(code,label)->
            Row(Modifier.fillMaxWidth().clickable{selected=code}.background(if(selected==code)Sage else Paper,RoundedCornerShape(12.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));if(selected==code)Icon(Icons.Default.Check,null,tint=Forest)}
        }
        OutlinedTextField(value=note,onValueChange={note=it.take(350)},label={Text("Noe mer? (valgfritt)")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp))
        SolidButton("Lagre",onClick={store.setReflection(selected,note);onClose()},modifier=Modifier.fillMaxWidth())
    }
}
@Composable private fun ReminderSheet(store:CoachStore,onPermission:()->Unit,onClose:()->Unit){
    var editing by rememberSaveable{mutableStateOf("")}
    var time by rememberSaveable{mutableStateOf("08:15")}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Rolige påminnelser",style=MaterialTheme.typography.headlineMedium)
        Text("Maks én hovedpåminnelse om morgenen. Andre varsler er frivillige.",color=Muted,fontSize=14.sp)
        listOf(Triple("morning","Morgen · dagens steg","08:15"),Triple("home","Hjem · initiativ","17:00"),Triple("evening","Kveld · refleksjon","20:45")).forEach{(kind,label,default)->
            val pref=store.reminders().optJSONObject(kind)
            val currentTime=pref?.optString("time",default)?:default
            Row(Modifier.fillMaxWidth().heightIn(min=58.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f).clickable{editing=kind;time=currentTime}){Text(label,fontSize=15.sp);Text("$currentTime · Endre",fontSize=12.sp,color=Muted)}
                Switch(pref?.optBoolean("on",false)?:false,onCheckedChange={store.setReminder(kind,it);if(it)onPermission()},colors=SwitchDefaults.colors(checkedTrackColor=Forest))
            }
        }
        if(editing.isNotEmpty()) {
            OutlinedTextField(value=time,onValueChange={time=it.take(5)},label={Text("Tidspunkt HH:MM")},singleLine=true)
            SoftButton("Lagre tidspunkt",onClick={store.setReminderTime(editing,time);editing=""})
        }
        SoftButton("Ferdig",onClose,Modifier.fillMaxWidth())
    }
}

@Composable private fun Onboarding(store:CoachStore,onNotifications:()->Unit,onFinished:()->Unit){
    var step by rememberSaveable { mutableIntStateOf(0) }
    // Bundled string survives rotation/process recreation; the user can change all choices later.
    var selection by rememberSaveable { mutableStateOf("family,work,training") }
    var saveError by remember { mutableStateOf(false) }
    val picked = selection.split(",").filter { it.isNotBlank() }
    val advance: () -> Unit = {
        if(step<2) { step += 1; saveError=false } else {
            if(store.setOnboarded(picked.toSet())){
                // Navigate explicitly after a successful, durable local write.
                onFinished()
                if(store.reminders().optJSONObject("morning")?.optBoolean("on")==true){
                    runCatching { onNotifications() }
                }
            } else {
                saveError=true
            }
        }
        Unit
    }
    Scaffold(
        modifier=Modifier.fillMaxSize(),
        containerColor=Paper,
        contentWindowInsets=WindowInsets.safeDrawing,
        bottomBar={
            Surface(color=Paper,shadowElevation=8.dp){
                Column(
                    Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start=24.dp,end=24.dp,top=12.dp,bottom=12.dp)
                ){
                    if(saveError){
                        Text("Kunne ikke lagre oppsettet. Prøv igjen. Ingen valg er slettet.",
                            color=MaterialTheme.colorScheme.error,
                            fontSize=13.sp,
                            modifier=Modifier.padding(bottom=8.dp))
                    }
                    SolidButton(
                        label=if(step==2)"Åpne NESTE" else "Fortsett",
                        onClick=advance,
                        modifier=Modifier.fillMaxWidth()
                    )
                    if(step>0) SoftButton("Tilbake",onClick={step--},modifier=Modifier.fillMaxWidth())
                }
            }
        }
    ){ insets ->
        Column(
            Modifier.fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal=24.dp)
                .padding(top=22.dp,bottom=28.dp)
        ){
            Row(verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(48.dp).background(Forest,RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center){
                    Text("N",fontSize=23.sp,color=White,fontWeight=FontWeight.Bold)
                }
                Spacer(Modifier.width(13.dp))
                Text("NESTE",fontSize=19.sp,fontWeight=FontWeight.SemiBold,letterSpacing=2.sp,color=Forest)
            }
            Spacer(Modifier.height(28.dp))
            MiniLabel("${step+1} AV 3")
            Spacer(Modifier.height(12.dp))
            Text(when(step){
                0->"Hva vil du ha mer av?"
                1->"Et lite dytt, når det passer."
                else->"Én ting av gangen."
            },style=MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text(when(step){
                0->"Velg inntil tre fokusområder. Dette kan endres senere."
                1->"NESTE kan sende et rolig morgenvarsel. Du styrer om det er på."
                else->"Ingen poeng, ingen skyld. Vi finner ett overkommelig steg i dag."
            },color=Muted,fontSize=16.sp,lineHeight=24.sp)
            Spacer(Modifier.height(24.dp))
            when(step){
                0->Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
                    store.areaNames.forEach { (area,label) ->
                        val active=area in picked
                        Surface(
                            shape=RoundedCornerShape(14.dp),
                            color=if(active)Sage else White,
                            onClick={
                                selection=when {
                                    active -> picked.filterNot { it==area }.joinToString(",")
                                    picked.size<3 -> (picked+area).joinToString(",")
                                    else -> selection
                                }
                            }
                        ){
                            Row(Modifier.fillMaxWidth().heightIn(min=58.dp).padding(horizontal=15.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                                Text(label,Modifier.weight(1f),color=Ink,fontSize=15.sp)
                                if(active) Icon(Icons.Default.Check,null,tint=Forest)
                            }
                        }
                    }
                }
                1->Surface(color=Sage,shape=RoundedCornerShape(19.dp)){
                    Row(Modifier.fillMaxWidth().padding(22.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.NotificationsActive,null,tint=Forest)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("08:15",fontSize=22.sp,fontWeight=FontWeight.Medium)
                            Text("Dagens lille steg er klart",fontSize=13.sp,color=Muted)
                        }
                    }
                }
                else->Surface(color=Forest,shape=RoundedCornerShape(24.dp)){
                    Column(Modifier.fillMaxWidth().padding(22.dp)){
                        MiniLabel("DITT FØRSTE STEG")
                        Spacer(Modifier.height(10.dp))
                        Text("Begynn der du er.",color=White,fontSize=25.sp,fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Text("Du kan gjøre enhver anbefaling mindre. Det er en funksjon, ikke et nederlag.",color=Color(0xFFDBE6DC),fontSize=15.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun NewCardSheet(store:CoachStore,onClose:()->Unit){
    var question by rememberSaveable{mutableStateOf("")}
    var answer by rememberSaveable{mutableStateOf("")}
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("Nytt læringskort",style=MaterialTheme.typography.headlineMedium)
        Text("Skriv et spørsmål du vil kunne svare på uten å slå opp.",color=Muted)
        OutlinedTextField(value=question,onValueChange={question=it.take(200)},label={Text("Spørsmål")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
        OutlinedTextField(value=answer,onValueChange={answer=it.take(900)},label={Text("Svar")},modifier=Modifier.fillMaxWidth(),minLines=2,shape=RoundedCornerShape(14.dp))
        SolidButton("Lagre kort",onClick={store.addCard(question,answer);onClose()},modifier=Modifier.fillMaxWidth(),enabled=question.isNotBlank()&&answer.isNotBlank())
    }
}

@Composable private fun ReviewCardSheet(store:CoachStore,onClose:()->Unit){
    var reveal by remember{mutableStateOf(false)}
    val current=store.dueCards().firstOrNull()
    if(current==null){
        Column(verticalArrangement=Arrangement.spacedBy(13.dp)){
            Text("Du er ajour",style=MaterialTheme.typography.headlineMedium)
            Text("Ingen kort venter på repetisjon.",color=Muted)
            SolidButton("Ferdig",onClose,Modifier.fillMaxWidth())
        }
    }else{
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
            MiniLabel("LÆRINGSKORT")
            Text(current.optString("question"),fontSize=23.sp,lineHeight=30.sp,fontWeight=FontWeight.Medium)
            if(reveal){
                Text(current.optString("answer"),fontSize=16.sp,color=Muted,lineHeight=24.sp)
                SolidButton("Husket",onClick={store.reviewCard(current.optString("id"),true);reveal=false},modifier=Modifier.fillMaxWidth())
                SoftButton("Prøv igjen senere",onClick={store.reviewCard(current.optString("id"),false);reveal=false},modifier=Modifier.fillMaxWidth())
            }else SolidButton("Vis svar",onClick={reveal=true},modifier=Modifier.fillMaxWidth())
        }
    }
}
