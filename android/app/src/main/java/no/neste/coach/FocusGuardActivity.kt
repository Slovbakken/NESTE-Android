package no.neste.coach

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg=Color(0xFFF6F5F0)
private val Ink=Color(0xFF202924)
private val Green=Color(0xFF213D32)
private val Soft=Color(0xFFE5EBE4)
private val Muted=Color(0xFF66736B)

class FocusGuardActivity:ComponentActivity() {
    private lateinit var settings:FocusGuardStore
    private var snapshot by mutableStateOf(ScreenTimeRepository.Snapshot(false,emptyList(),0L))
    private var statusRevision by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        settings=FocusGuardStore(this)
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=Green,background=Bg,surface=Color.White)){
                FocusGuardPage(
                    settings=settings,
                    snapshot=snapshot,
                    revision=statusRevision,
                    onChanged={statusRevision++},
                    onBack={finish()},
                    onUsageSettings={startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))},
                    onAccessibilitySettings={startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))},
                    onRefresh={refresh()}
                )
            }
        }
    }
    override fun onResume(){super.onResume();refresh()}
    private fun refresh(){
        statusRevision++
        lifecycleScope.launch {
            snapshot=withContext(Dispatchers.IO){ScreenTimeRepository(this@FocusGuardActivity).read()}
        }
    }
}

@Composable private fun FocusGuardPage(
    settings:FocusGuardStore,
    snapshot:ScreenTimeRepository.Snapshot,
    revision:Int,
    onChanged:()->Unit,
    onBack:()->Unit,
    onUsageSettings:()->Unit,
    onAccessibilitySettings:()->Unit,
    onRefresh:()->Unit
) {
    @Suppress("UNUSED_VARIABLE")val revisionObserved=revision
    val consent=settings.consent()
    val access=settings.serviceActive()
    val days=settings.observedDays()
    val rules=settings.rules()
    val running=settings.enabled()
    var selected by rememberSaveable{mutableStateOf(rules.map{it.pkg}.joinToString("|"))}
    val chosen=selected.split("|").filter{it.isNotBlank()}.toSet()
    val allApps=snapshot.days.flatMap{it.apps}
        .filter{FocusGuardStore.canRestrict(it.packageName)}
        .groupBy{it.packageName}
        .map{(pkg,items)->Triple(pkg,items.first().displayName,items.sumOf{it.milliseconds})}
        .sortedByDescending{it.third}.take(22)

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()){
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=24.dp,top=10.dp),
            verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=onBack){Text("‹  Tilbake",color=Green)}
            Spacer(Modifier.weight(1f))
            Text("NESTE / FOKUSVERN",fontSize=11.sp,letterSpacing=1.sp,color=Muted)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start=24.dp,end=24.dp,top=20.dp,bottom=30.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text("Mer tid til det du vil.",fontSize=31.sp,lineHeight=36.sp,fontWeight=FontWeight.SemiBold,color=Ink)
            Text("Først se mønsteret. Så velge hvilke apper du vil bruke mindre. NESTE hjelper deg å holde grensene du selv bestemmer.",
                color=Muted,lineHeight=22.sp,fontSize=14.sp)
            if(!snapshot.hasAccess){
                FocusCard {
                    Text("1 · Tillat skjermtidsmåling",fontWeight=FontWeight.SemiBold,color=Ink)
                    Text("NESTE må kunne måle hvor lenge appene brukes. Det gis i Android-innstillinger.",fontSize=13.sp,color=Muted)
                    FocusButton("Åpne brukstilgang",onUsageSettings)
                }
            }else if(!consent){
                FocusCard {
                    Text("2 · Samtykke til Fokusvern",fontWeight=FontWeight.SemiBold,color=Ink)
                    Text("NESTE leser hvilke apper som er i forgrunnen, men ikke meldinger, tastetrykk, skjerminnhold eller nettleserhistorikk. Dersom du aktiverer sperren, brukes denne informasjonen utelukkende for å avbryte apper du har valgt. Alt skjer lokalt, og du kan når som helst slå av tjenesten.",
                        fontSize=14.sp,lineHeight=20.sp,color=Muted)
                    Text("En egen Android-tillatelse for tilgjengelighet trengs for blokkering. Ingen app sperres før du velger den og godkjenner planen.",
                        fontSize=13.sp,color=Muted)
                    FocusButton("Jeg samtykker til denne bruken"){
                        settings.acceptConsent(true);settings.startObservation();onChanged()
                    }
                }
            }else{
                FocusCard {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(if(running)"Fokusvern er aktivt" else "Observasjon og oppsett",
                            fontWeight=FontWeight.SemiBold,color=Ink,modifier=Modifier.weight(1f))
                        Text(if(running)"PÅ" else "AV",color=if(running)Green else Muted,fontWeight=FontWeight.Bold)
                    }
                    Text(if(days<7)"Observasjon: "+days+" av 7 dager"
                        else "Observasjon: minimum 7 dager fullført",fontSize=13.sp,color=Muted)
                    LinearProgressIndicator(progress={ (days/7f).coerceIn(0f,1f) },
                        modifier=Modifier.fillMaxWidth(),color=Green,trackColor=Soft)
                    if(running) {
                        Text("Dagens grenser for valgte apper:",fontSize=13.sp,color=Muted)
                        rules.forEach {rule->
                            Text(rule.pkg.substringAfterLast('.')+" · "+settings.currentLimit(rule)+" min",
                                fontSize=13.sp,color=Ink)
                        }
                        FocusButton("Slå av Fokusvern"){
                            settings.stop();onChanged()
                        }
                    }
                }
                FocusCard {
                    Text("Sperren trenger egen tillatelse",fontWeight=FontWeight.SemiBold,color=Ink)
                    Text("Androids tilgjengelighetstjeneste ser bare navnet på appen som åpnes. NESTE viser en sperreskjerm når en valgt app når grensen. Hele telefonen låses aldri.",
                        fontSize=13.sp,lineHeight=20.sp,color=Muted)
                    Text(if(access)"Tilgjengelighet: aktivert" else "Tilgjengelighet: ikke aktivert",
                        color=if(access)Green else Muted,fontWeight=FontWeight.Medium,fontSize=13.sp)
                    FocusButton("Åpne tilgjengelighetstillatelser",onAccessibilitySettings)
                }
                FocusCard {
                    Text("Hvordan strengt skal det være?",fontWeight=FontWeight.SemiBold,color=Ink)
                    listOf(
                        "mild" to "Mild · bare et varsel",
                        "balansert" to "Balansert · pause og overstyring",
                        "streng" to "Streng · sperre etter grensen"
                    ).forEach {(id,label)->
                        Row(Modifier.fillMaxWidth().clickable{settings.setMode(id);onChanged()}
                            .padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                            RadioButton(selected=settings.mode()==id,onClick={settings.setMode(id);onChanged()})
                            Text(label,fontSize=14.sp,color=Ink)
                        }
                    }
                    Text("I streng modus kan du fortsatt åpne NESTE og slå av Fokusvern. En nødvendig femminutters overstyring er alltid mulig.",
                        fontSize=12.sp,color=Muted,lineHeight=18.sp)
                }
                FocusCard {
                    Text("Velg appene du vil begrense",fontWeight=FontWeight.SemiBold,color=Ink)
                    Text("Du velger selv. Systemfunksjoner og viktige apper er unntatt.",
                        fontSize=13.sp,color=Muted)
                    if(allApps.isEmpty())Text("Ingen bruksdata ennå. Åpne NESTE igjen etter at du har brukt telefonen.",
                        fontSize=13.sp,color=Muted)
                    allApps.forEach{(pkg,label,ms)->
                        val marked=pkg in chosen
                        Row(Modifier.fillMaxWidth().clickable {
                            selected=if(marked)chosen.minus(pkg).joinToString("|")
                            else if(chosen.size<8)chosen.plus(pkg).joinToString("|") else selected
                        }.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked=marked,onCheckedChange={
                                selected=if(it) {if(chosen.size<8)chosen.plus(pkg).joinToString("|") else selected}
                                else chosen.minus(pkg).joinToString("|")
                            })
                            Column(Modifier.weight(1f)){
                                Text(label,color=Ink,fontSize=14.sp,maxLines=1)
                                Text("Gj.snitt siste dager: "+settings.observedAverage(pkg,snapshot)+" min/dag",
                                    color=Muted,fontSize=11.sp)
                            }
                        }
                    }
                    if(chosen.isNotEmpty()){
                        Text(chosen.size.toString()+" av 8 apper valgt",fontSize=12.sp,color=Muted)
                        if(!access) {
                            Text("Aktiver tilgjengelighetstillatelsen før du tester.",fontSize=12.sp,color=Muted)
                        }else{
                            FocusButton("Test sperren i 2 minutter"){
                                settings.allowTest(chosen.first());onChanged()
                            }
                            Text("Test: Åpne "+chosen.first().substringAfterLast('.')+
                                " manuelt fra telefonen. Sperren blir ikke stående permanent.",
                                fontSize=12.sp,color=Muted,lineHeight=18.sp)
                        }
                    }
                }
                FocusCard {
                    Text("Klar for en gradvis plan?",fontWeight=FontWeight.SemiBold,color=Ink)
                    Text("Etter minst syv observasjonsdager kan du godkjenne startgrensen. Den tar utgangspunkt i faktisk bruk og kan reduseres med 10 % i uker som ser håndterbare ut. Nedtrappingen stopper midlertidig ved vansker.",
                        fontSize=13.sp,lineHeight=20.sp,color=Muted)
                    FocusButton(if(running)"Oppdater valgte apper" else "Godkjenn og start Fokusvern",
                        enabled=days>=7 && chosen.isNotEmpty() && access){
                        settings.approvePlan(chosen,snapshot);onChanged()
                    }
                    if(days<7)Text("Du kan teste sperren nå, men nedtrapping starter først etter observasjonsperioden.",
                        fontSize=12.sp,color=Muted)
                }
                Text("Ingen app låses på systemnivå. På enkelte Android-modeller kan tilgjengelighetstjenesten stoppes av systemet. Tilgjengelighetstilgangen kan slås av i Android-innstillinger.",
                    color=Muted,fontSize=12.sp,lineHeight=18.sp)
                TextButton(onClick={settings.acceptConsent(false);onChanged()}){
                    Text("Trekk samtykke og stopp Fokusvern",color=Muted)
                }
            }
        }
    }
}

@Composable private fun FocusCard(content:@Composable ColumnScope.()->Unit){
    Surface(color=Color.White,shape=RoundedCornerShape(20.dp)){
        Column(Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable private fun FocusButton(label:String,enabled:Boolean=true,onClick:()->Unit){
    Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
        colors=ButtonDefaults.buttonColors(containerColor=Green),
        shape=RoundedCornerShape(14.dp)){
        Text(label,fontWeight=FontWeight.Medium)
    }
}
