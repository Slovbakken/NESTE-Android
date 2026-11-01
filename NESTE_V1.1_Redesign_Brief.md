# NESTE 1.1 — Native Motion
## Full redesign- og implementeringsbrief

**Status:** Klar for design og utvikling · **Dato:** 1. november 2026  
**Produkt:** NESTE – En ting av gangen  
**Målplattform:** Android, mobil først  
**Grunnlag:** Gjennomgått NESTE 1.0-kildekode, README, testlogg og faktiske skjermbilder.

---

## 0. Produktbeslutningen

NESTE skal ikke se ut som en responsiv nettside. Det skal oppleves som en rolig, gjennomarbeidet Android-app der brukeren går fra **forslag → handling → tilbakemelding** med få trykk. Versjon 1.1 er en **opplevelses- og arkitekturforbedring**, ikke en utvidelse med flest mulig funksjoner.

**Produktløfte:** «NESTE hjelper meg å se hva som er verdt å gjøre, og å komme i gang – også når overskuddet er lavt.»

**Primærscenario:** Brukeren åpner morgenvarselet, ser én relevant anbefaling, velger et lite steg, gjennomfører eller justerer det, og lukker appen igjen. Dette bør vanligvis kunne skje uten scrolling.

**Designord:** *Calm. Tactile. Human. Focused. Confident.*  
**Aldri:** konfetti, XP, streak-straff, generisk AI-glød, gradientklatter, overlessede dashboards, «du ligger bakpå»-meldinger eller lange kortlister.

### Hvorfor 1.0 føles som web

- Gjeldende Android-app er en **WebView** som viser `web/index.html`, `styles.css` og `app.js`.
- Skjermene har sideoverskrifter, en felles header/logo på hver side og flere kort stablet som dokumentseksjoner.
- Dialogene er midtstilte web-modaler, mens native Android vanligvis bruker navigasjonsstakk, kontekstuelle sheets og systembevegelser.
- Gjeldende hjemskjerm lar registreringer og sekundærinformasjon konkurrere med dagens primærhandling.

**Konsekvens:** Bytt hovedgrensesnittet til **Kotlin + Jetpack Compose**. Bevar eksisterende lokal coach-logikk og data gjennom en eksplisitt migrering. Å legge på flere CSS-animasjoner i WebView vil ikke være tilstrekkelig som hovedløsning.

---

## 1. Informasjonsarkitektur og navigasjon

### Global navigasjon

**Fire destinasjoner + én sentral hurtighandling** i en fast native bunnlinje:

| Posisjon | Navn | Funksjon |
|---|---|---|
| 1 | **I dag** | Dagens anbefaling og neste konkrete steg |
| 2 | **Plan** | Egne oppgaver, kommende forpliktelser og ukefokus |
| 3 | **Fang opp** | Fremhevet `+` som åpner et bottom sheet, **ikke** en egen destinasjon |
| 4 | **Utvikling** | Faktiske handlinger, mønstre og ukerefleksjon |
| 5 | **Meg** | Mål, varselpreferanser, data, tilgang og personvern |

Bunnlinjen er synlig på rotvisningene og skjules i fullskjerms «Gjennomfør»-flyt. Trykk på aktiv fane scroller til toppen, ikke til en ny skjerm. Systemets tilbakegest går tilbake i navigasjonsstakken og lukker bottom sheets før den forlater appen.

### Hierarki

```text
Oppstart (kun første gang)
 └─ Velg fokus → Velg varselrytme → Første handling

I dag
 ├─ Dagens handling → Fokusvisning → Fullført / Mindre steg / Avslutt
 ├─ Hvorfor akkurat dette? (bottom sheet)
 ├─ Endre dagens handling (bottom sheet)
 ├─ Rask energijustering (bottom sheet)
 └─ Kveldsrefleksjon (bottom sheet)

Plan
 ├─ I dag / Snart / Senere (segmenter)
 ├─ Oppgavedetalj
 ├─ Rediger ansvar og repetisjon
 └─ Ukens fokus

Fang opp (+)
 └─ Fang opp-sheet → Velg type → Lagre → Tilbake til der du var

Utvikling
 ├─ Denne uken / Forrige uke
 ├─ Daglig historikk
 └─ Ukesoppsummering

Meg
 ├─ Fokusområder
 ├─ Varsler
 ├─ Valgfri skjermtidstilgang
 ├─ Data: eksport / import / sletting
 └─ Om og personvern
```

---

## 2. Kjerneflyter – detaljert atferd

### Flyt A: Fra morgenvarsel til handling

1. Ett rolig varsel på brukerens valgte tidspunkt: «Dagens lille steg er klart» + én konkret anbefaling. Unngå skyldskapende formuleringer.
2. Trykk åpner **I dag**, med dagens handling umiddelbart synlig. Ingen splash-animasjon som forsinker tilgang.
3. Hero-området viser **kategori**, **handlingstittel**, **én konkret instruksjon** og CTA **«Start»**.
4. Sekundære alternativer: **«Gjør mindre»**, **«Bytt»**, **«Hvorfor?»**.
5. **Start** åpner en ren fokusvisning med første handling. Timer er valgfri, ikke et krav.
6. Avslutning: **«Gjort»**, **«Delvis gjort»**, **«Ikke nå»**. Ingen straff, ingen streak-måling.
7. Ved «Gjort» får brukeren en subtil visuell og taktil bekreftelse; deretter vises «Dagens viktigste er gjort» og maks ett frivillig neste forslag.

**Antall trykk:** Morgenvarsel → Start → Gjort = normalt 2 trykk i appen.

### Flyt B: «Jeg orker ikke dette»

1. Trykk **«Gjør mindre»** i hero eller fokusvisningen.
2. Bottom sheet viser forslag i to størrelser: **«2 minutter»** og **«Bare første steg»**, hver med konkret tekst.
3. Brukeren velger og returnerer til samme fokusvisning. Endringen registreres som tilpasning, **ikke nederlag**.
4. Hvis også minsteversjonen blir for mye, tilby **«Ikke i dag»**, samt eventuelt et restitusjonsforslag som **erstatter** den opprinnelige handlingen – ikke legges oppå.

### Flyt C: «Jeg kommer på noe som må huskes»

1. Trykk midtknappen **+ Fang opp** fra hvilken som helst rotvisning.
2. Ett tekstfelt med autofokus og tittel «Hva må du ta tak i?».
3. Valgfrie små kategorichips: **Hjem**, **Jobb**, **Husk**, **Idé**.
4. Valgfri dato eller repetisjon. Hurtigvalg: **I morgen**, **Denne uken**, **Ingen dato**.
5. Trykk **«Lagre»**. Sheet glir ned, det gis lett haptisk kvittering, og brukeren blir på opprinnelig skjerm.
6. Elementet går til Plan. Har det frist, kan coachen senere foreslå det, men den gjør ikke alle nye notater om til påtrengende varsler.

### Flyt D: En lett kveldsrefleksjon

1. Valgfritt kveldspush; kan slås helt av.
2. Ett enkelt sheet, maks to trinn: «Hva fungerte?» og eventuelt «Hva gjorde det vanskelig?».
3. Ett-trykksvalg (f.eks. *lett å starte*, *for stort steg*, *feil tidspunkt*, *lite energi*) og valgfri fritekst.
4. Resultatet brukes til å tilpasse **neste** forslag. Ingen daglig loggbok-plikt.

---

## 3. Skjermspesifikasjoner

### 3.1 I dag – appens viktigste skjerm

**Første skjermbilde, topp til bunn:**

1. Diskret dato og en liten avatar/innstillingsinngang; ingen stor NESTE-logo på hver skjerm.
2. Kort, menneskelig hilsen: «God morgen» / «God ettermiddag» uten tekstblokker.
3. Lett trykkbar statusrad: **«Kapasitet i dag: Normal · Endre»**, eller «Ikke valgt».
4. **Primærhandling** som ett stort, tydelig fokusområde (ikke nødvendigvis et innrammet kort):
   - etikett «DET VIKTIGSTE I DAG»
   - kategori i ren tekst
   - overskrift på maks tre linjer
   - én setning som beskriver første steg
   - solid primærknapp «Start»
   - to diskrete sekundærhandlinger «Gjør mindre» og «Bytt»
5. En lavmælt «Senere i dag»-rad (maks ett kort) dersom relevant.

**Tre tilstander:**
- **Ikke startet:** handlingsfokus dominerer.
- **Pågår:** vis «Fortsett der du slapp» og gjenoppta samme steg/timer.
- **Gjort:** hero får roligere overflate og tydelig kvittering, med mulighet for frivillig ekstrahandling.

**Uten tilgjengelig anbefaling:** Forklar hvorfor («Du har satt alle fokusområder på pause»), og gi én tydelig løsning. Ingen tomme placeholder-kort.

### 3.2 Fokusvisningen – dedikert fullskjerm

- Fullskjerm, nedtonet navigasjon; Android tilbake-gest returnerer til I dag uten å miste progresjon.
- Tydelig ett-steg-instruksjon midt i skjermen, maks to tekstblokker.
- Valgfri, synlig men dempet **5-minutters timer**: Start / Pause / Avslutt.
- Permanent tilgjengelig «Gjør mindre».
- Nederst primær «Gjort», sekundær «Ikke nå».
- Ingen automatisk lyd, fullskjermfeiring eller skjerm som tvinger brukeren til å skrive refleksjon.

### 3.3 Plan – ikke enda en oppgaveliste

- Kompakt topp «Det du har foran deg».
- Segmentert filter **I dag · Snart · Senere** (alternativt horisontalt sveip med synlig valg).
- Gruppér etter tidsmessig relevans, ikke 7 kategorikort på en gang.
- Hvert radobjekt har tittel, kategori, eventuelt dato, og ett tydelig fullføringsvalg.
- Vis **«Eid ansvar»** som egen markør på familieoppgaver: ting brukeren selv har påtatt seg å følge opp.
- Maks én overordnet «Ukens fokus»-stripe. Redigering ligger i eget sheet.
- Ved tom liste: «Ingenting fanget opp ennå. Legg til det som ellers ville blitt glemt.» med handling «Fang opp».

### 3.4 Fang opp – globalt handlingssheet

- Starter rundt 65–75 % av skjermhøyden og kan utvides naturlig med tastatur.
- Tekstfelt på toppen; minimal friksjon.
- Kategorivalg og datovalg under; avansert redigering er frivillig.
- «Lagre» synlig over tastaturet ved hjelp av IME-insets.
- Avbrutt utkast beholdes frem til sheet lukkes eller brukeren bekrefter forkasting ved ikke-tomt felt.
- Etter lagring: «Lagret i Plan» i subtil snackbar.

### 3.5 Utvikling – forstå, ikke prestere

- Tittel «Det som faktisk virker».
- Ukevelger og én kompakt oppsummering: antall **gjennomførte** og **forenklede** handlinger, med datoperiode.
- Enkel 7-dagers indikator. Skillet mellom «ingen registrering» og «ikke gjort» må være tydelig.
- «Hva har hjulpet?» presenteres kun når det faktisk er nok selvrapportering til å støtte en observasjon.
- Siste handlinger ligger som kompakte rader; ingen tomme dashboardkort.
- Ingen fiktiv fremgangsprosent, helbredelsesprognose, motivasjonsscore eller klinisk tolkning.

### 3.6 Meg

- Personlige fokusområder og prioriteringer.
- Varselrytme: tidspunkt, antall og stilleperioder.
- Skjermtid: separat frivillig systemtillatelse og tydelig forklaring på hva som leses.
- Eksporter/importer data og full sletting.
- Tilgjengelighet: «Redusert bevegelse», «Haptiske tilbakemeldinger».
- Ikke opprett konto eller krev skytilgang i 1.1.

### 3.7 Oppstart – tre lette steg

1. **Hva vil du ha mer av?** Velg maks tre fokusområder fra syv eksisterende; resten kan endres senere.
2. **Når er et dytt nyttig?** Velg morgenvarsel og eventuelt kveld. Be om Android-varseltilgang etter at brukeren har forstått nytten og aktivert varslene.
3. **Begynn veldig lite.** Vis første konkrete anbefaling, og tilby å åpne I dag.

Allerede onboardede V1-brukere skal **aldri** tvinges gjennom oppstart på nytt.

---

## 4. Visuell identitet – «rolig premium»

### Fargetokens

| Token | HEX | Formål |
|---|---|---|
| `background` | `#F6F5F0` | Varm nøytral hovedflate |
| `surface` | `#FFFFFF` | Sheets og sentrale interaksjonsflater |
| `ink` | `#202924` | Primærtekst |
| `muted` | `#66736B` | Sekundærtekst |
| `forest` | `#213D32` | Hovedhandling og merkevare |
| `forestPressed` | `#172F26` | Trykket tilstand |
| `sage` | `#E5EBE4` | Milde markeringer |
| `line` | `#DCE1D8` | Delere, ikke kort rundt alt |
| `warmAccent` | `#B16F50` | Sjeldne informative varsler, aldri dekor |

Ingen dekorative gradienter. Maks én sterkt kontrasterende primærflate per skjerm. Dark mode kan spesifiseres senere, men konstruksjonen må bruke tokens fra start.

### Typografi

- Android-native sans: **Roboto/Roboto Flex**, optisk ryddig, tilgjengelig og rask; ingen webfontavhengighet.
- Stor handlingsoverskrift **32–36 sp**, normal/semibold og tett linjeavstand (ikke massiv dekorativ serif).
- Skjermoverskrifter **25–28 sp**.
- Brødtekst **15–16 sp**, linjehøyde ~1,4.
- Etiketter **11–12 sp** med moderat spacing; aldri mikrotekst som bærer nødvendig informasjon.
- Tall benytter tabulære sifre der det er relevant.

### Form og plass

- 8 dp grunnrytme; 4 dp for små justeringer.
- Horisontal innholdsmarg **24 dp** (20 dp på smal skjerm).
- Store flater **20–24 dp** hjørneradius; sheets **28 dp** på toppen; små kontroller **12–16 dp**.
- Primærknapp **minst 52 dp høy**, berøringsmål for alle handlinger **minst 48 × 48 dp**.
- Maks én tung hero-flate; ellers typografi, spacing og enkle skillestreker.
- Bruk lineære, konsistente outline-ikoner. Ikke symboler, gnister eller AI-stjerner som pynt.

### Viktige antivalg

- Ikke gjenta en navnelogo på alle skjermene.
- Ikke legg inn bunnløse «card stacks» for hver kategori.
- Ikke skjul kritiske handlinger bak kun swipe eller langtrykk.
- Ikke legg inn «energibar», poeng eller fiktive «AI-analyser».
- Ingen store illustrasjoner i tomtilstander; godt språk og én løsning er bedre.

---

## 5. Motion-design – spesifisert og byggbart

**Motion må kommunisere endring i struktur eller status.** Hvis en animasjon ikke hjelper orientering eller forståelse, fjernes den.

| Interaksjon | Animasjon | Standardvarighet | Følelse |
|---|---|---:|---|
| Bytt bunnfane | Lett fade + 6–10 dp horisontal forskyvning | 180–220 ms | Diskret |
| Åpne dagens handling | Shared bounds fra hero til fokusvisning | 300–360 ms | Sammenhengende |
| Åpne bottom sheet | Vertikal spring fra nedre kant + dim | ~280–340 ms | Native og taktil |
| Trykk på primærknapp | 0,98 skala og bakgrunnsendring | 90–140 ms | Responsiv |
| Fullfør handling | Check-stroke + rolig tekstbytte, ingen konfetti | 240–320 ms | Ferdig, ikke jubel |
| Endre til mindre steg | AnimatedContent med kort høydeendring | 180–250 ms | Umiddelbart enklere |
| Swipe på oppgave | Følger fingeren; terskel + rolig tilbakefjæring | Geststyrt | Kontrollerbart |
| Oppdatere uketall | Kort opptelling ved første visning | maks 350 ms | Informativt |

- Bruk systemets **predictive back** og la systemgesten styre tilbakebevegelse.
- Én subtil haptisk bekreftelse ved *lagret* eller *fullført*; ingen vibrasjon på hver navigering.
- Med «Redusert bevegelse» / systemets animasjonsinnstillinger: fjern bevegelsestransformasjoner; behold nødvendige statusendringer som umiddelbar tilstandsoppdatering.
- Ingen loopende animasjoner på hjemskjermen; skånsom på batteri.
- **60 fps er målet** på vanlig Android-mobil; ingen tung blur eller store repaint-effekter på hver frame.

---

## 6. Coach-atferd, innhold og grenser

NESTE 1.1 skal være **regelbasert og offline**, men føles personlig ved at reglene tar hensyn til brukerens egne mål, oppgaver, valgt kapasitet, dato og tidligere gjennomføring. Ikke presenter dette som en generativ AI som observerer brukerens liv.

### En enkel prioritetsmodell

1. Respekter eksplisitte frister og ansvar brukeren selv har lagt inn.
2. Ta hensyn til områdenes prioritering og unngå gjentakelse av samme type forslag for tett.
3. Hvis kapasitet er lav eller søvn selvrapporteres dårlig: **nedskaler eller reduser**, ikke send flere oppgaver.
4. Hvis brukeren velger «for stort steg» flere ganger: foreslå mindre format neste gang.
5. Hjemmeforslag skal stimulere til **eget eierskap** til oppgaver, ikke overlate planleggingsansvaret til samboeren.
6. Varsling skal aldri eskalere ved manglende respons; ignorerte varsler bør føre til lavere trykk, ikke purring.

### Pushregler

- Standard: **én planlagt morgenpåminnelse**, valgfri kveldsrefleksjon.
- Varsler er handlingsorienterte, korte og uten skyldbudskap.
- Hvis dagens handling er gjort, send ikke et «du har ikke gjort»-varsel senere.
- Åpning fra varsel tar brukeren direkte til riktig kontekst.
- Gi klar varselinnstilling og pausefunksjon.

### Energi og innsjekk

- Rask daglig kapasitetsindikator: **Lite / Normalt / Mye** – helt frivillig.
- En valgfri utvidet innsjekk kan ha energi, stress, søvn og fungering på **0–10** for dem som ønsker oppfølging.
- V1 har historiske energi-/søvnverdier på **1–5**. Bevar disse med opprinnelig skala; **ikke konverter dem stille til 0–10** eller sammenlign direkte.
- Ingen helsediagnose, utbrenthetsskår eller sikre årsakspåstander. Ved vedvarende eller økende funksjonssvikt kan appen nøkternt foreslå å snakke med fastlege.

### Eksempel på microcopy

| Situasjon | Bruk | Unngå |
|---|---|---|
| Lav energi | «Vi gjør dette mindre i dag.» | «Du må presse deg.» |
| Utsatt handling | «Vil du prøve et mindre første steg?» | «Du brøt streaken din.» |
| Fullført | «Bra. Det viktigste er gjort.» | «EPISK! +100 XP!» |
| Familie | «Sjekk morgendagens klær og utstyr.» | «Spør samboeren hva du skal gjøre.» hver dag |
| Arbeid | «Åpne saken og skriv første setning.» | «Løs hele problemet nå.» |
| Ingen data | «Ingen registrering denne uken.» | «0 % fremgang.» |

---

## 7. Teknisk plan og migrering fra 1.0

### Teknologibeslutning

- **Kotlin + Jetpack Compose + Material 3** for all primærnavigasjon og alle kjernevisninger.
- **Navigation Compose** for ruter og Androids tilbakeatferd.
- **Room** for oppgaver, handlinger, historikk, innsjekk og læringskort.
- **DataStore** for fokusområder, varselpreferanser og visningsinnstillinger.
- Gjenbruk den funksjonelle logikken fra `web/app.js` som *spesifikasjon*; portér regelmotoren til testbar Kotlin i stedet for å ha to ulike autoritative motorer.
- Behold native varselintegrasjon / `ReminderReceiver` der det er forsvarlig, og test på faktiske Android-enheter.
- Ikke legg til konto, sporing, nettverkskall eller nettverkstillatelse i denne versjonen.

### Obligatorisk databro for eksisterende brukere

1. Ta sikkerhetskopi av det gamle JSON-formatet før migrering.
2. I eksisterende app brukes WebView-lagringsnøkkelen **`neste_state_v1`** under `file:///android_asset/index.html`.
3. Ved første oppdaterte oppstart: les eksisterende verdi med en **midlertidig kompatibilitets-WebView** som bruker samme gamle origin, etter at dokumentet er lastet. Parse og valider data.
4. Flytt onboardingstatus, prioriteringer, fullføringshistorikk, planoppgaver, repetisjonskort og lagrede innstillinger til native lagring.
5. Merk migrering som fullført bare når hele transaksjonen er skrevet og validert. Ved feil: behold originaltilstand og tilby JSON-import i stedet for å overskrive.
6. La eksisterende Android-pakkenavn og signering beholdes ved oppdatering. Test **1.0 installert → oppgrader til 1.1 → historikk intakt**.
7. Behold lokal JSON-eksport/import for sikkerhetskopiering.

**Viktig:** `localStorage` kan ikke leses direkte av Kotlin som om det var en vanlig fil; origin og WebView-dataprofil må testes. Migreringen er en egen testbar leveranse, ikke et lite etterarbeid.

### Android-krav

- Respekter `WindowInsets` for statusbar, gesture-nav og tastatur; full **edge-to-edge**.
- Håndter systemets tilbakegest og avbrytbare sheet-bevegelser.
- Støtt tastatur, TalkBack, stor skrift og liggende skjerm uten tap av data.
- Skjermtidstilgang er helt frivillig og skal vise begrunnelse før Androids tillatelsesskjerm.
- Ikke lagre diagnostikk eller personlige opplysninger eksternt.

---

## 8. Prioritert byggeplan

### P0 – Må være ferdig før første 1.1-APK

1. Native Compose-skall, edge-to-edge, bunnnavigasjon og tilbakegest.
2. Native I dag, hero-til-fokusvisning og primærhandlingene Start / Gjør mindre / Gjort / Ikke i dag.
3. Fang opp-sheet og Plan med fungerende lokal lagring.
4. Coach-reglene portert fra 1.0; samme handlingsinnhold, korrekte datoregler og ingen regresjon.
5. Migrering fra `neste_state_v1`, JSON-eksport og nødvendig dataintegritet.
6. Ekte native varsler, deep links og innstillinger.
7. Motion primitives, reduserte animasjoner og nødvendige haptics.
8. Testet debug-APK på minst én ekte Android-enhet.

### P1 – Ferdigstiller opplevelsen

9. Native Utvikling med tydelig historikk og datamangelspråk.
10. Native Meg, fokusområder, skjermtidstilgang, varsler og databehandling.
11. Kveldsrefleksjon som bottom sheet.
12. Polish av tomtilstander, first-run, dark-mode-tokenstruktur og skjermleserstøtte.

### P2 – Etter 1.1, ikke la dette blokkere UI-løftet

13. Finere treningsflyt med innhold/progresjon.
14. Flere forslag per område og bedre kalibrering av personlige preferanser.
15. Valgfri kalenderintegrasjon, kun etter separat samtykke og avklaring.
16. Eventuell generativ AI-coach, først når personverngrenser og brukeropplevelse er klare.

---

## 9. Akseptansekriterier / QA

**En versjon er ikke ferdig bare fordi skjermene ser riktige ut.** Følgende må bestås:

- [ ] Appen starter uten nettsideaktig header/sidebar eller en lang skrollende startside.
- [ ] Brukeren ser dagens prioriterte handling uten scrolling på 393 × 852 dp-lignende mobilvisning, med normal tekststørrelse.
- [ ] Morgenvarsel → I dag → Start → Gjort fungerer og lagres etter full omstart.
- [ ] Startet men ikke ferdig: naviger bort og tilbake; tilstanden er fortsatt «Pågår».
- [ ] «Gjør mindre» endrer det konkrete steget og teller ikke som feilet forsøk.
- [ ] «Ikke i dag» gir ingen streak-straff og ingen eskalering av varsler.
- [ ] Fang opp fra alle hovedfaner, skriv, lagre; oppgaven finnes i Plan etter restart.
- [ ] Sheet åpnes/lukkes med touch, Android tilbakegest og tastatur uten skjult CTA.
- [ ] Swipefunksjoner har alltid synlige tilgjengelige alternativer.
- [ ] Trykk på back under fullføringsanimasjon gir ingen dobbelt registrering.
- [ ] Tall for ingen registrering vises ikke som null fremgang eller prosentandel.
- [ ] Eldre 1–5-innsjekker vises fortsatt med korrekt skala etter migrering.
- [ ] V1-testdata med oppgaver, historikk, fokusområder og læringskort migreres tapsfritt.
- [ ] Varsler respekterer avslag, deaktivert varselkategori, omstart og endring av tidspunkt.
- [ ] Ingen data sendes ut av appen uten eksplisitt eksport eller valg.
- [ ] Støtte for TalkBack, 200 % skriftstørrelse, og redusert bevegelse uten skjulte funksjoner.
- [ ] Faktisk APK-bygg, installering, opplasting som oppdatering og kaldstart verifisert.

### Testapparater og skjermbredder

- Minst én fysisk Android-telefon med gesture navigation.
- Smal skjerm rundt 360 dp, normal 393 dp, bred rundt 430 dp.
- Tastatur åpent, store systemfonter, mørkt systemtema, varsler av og Doze/batterisparing.
- Forskjellige tidspunkter rundt midnatt, lokal **Europe/Oslo**, samt sommertid/vintertid.

---

## 10. Kort handoff til utvikleren

> **Oppgave:** Gjennomfør NESTE 1.1-redesign i eksisterende Android-prosjekt. Dette er en native-first UX-oppgradering, ikke en ny webdesign. Les 1.0-kode og denne briefen før endringer. Bruk Kotlin/Jetpack Compose for ny hoved-UI. Bevar lokal funksjonalitet, dataintegritet, coach-regler, varslingslogikk og eksisterende brukeres data. Bygg navigasjon, I dag, fullskjerm fokus, Fang opp-sheet, Plan, Utvikling og Meg etter spesifikasjonen. Legg inn reduserte animasjoner, TalkBack og Android back/edge-to-edge. Ikke introduser konto, sky-AI, analytics, poeng eller nye tillatelser. Lever kildekode, screenshots fra faktisk app, testresultater og installérbar APK; oppgi eksplisitt hva som er reelt verifisert og hva som gjenstår. Ingen påstått ferdig APK uten verifisert bygg.

**Ferdig-definisjon:** Appen oppleves naturlig i Android-hånden, de viktigste handlingene er maks to–tre trykk unna, lagret historikk er intakt, og skjermene gjør det enklere å handle – ikke bare penere å lese.

---

## Relevante Android-kilder

- [Android: edge-to-edge i Jetpack Compose](https://developer.android.com/develop/ui/compose/system/setup-e2e)
- [Android: predictive back i Compose](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)
- [Android: ModalBottomSheet i Compose](https://developer.android.com/develop/ui/compose/quick-guides/content/create-bottom-sheet)
- [Android: shared element transitions](https://developer.android.com/develop/ui/compose/animation/shared-elements)
