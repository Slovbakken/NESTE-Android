# NESTE 1.1 – Teknisk verifikasjon

## Testet lokalt

- [x] Utpakking av original 1.0-kildepakke og lesing av redesignbrief.
- [x] Ekstraksjon av 29 originale handlingsforslag til Android `actions.json`.
- [x] Statisk kontroll av native Compose-kilde, app- og navigasjonsstruktur.
- [x] Statisk kontroll av migreringsnøkkel og URL `file:///android_asset/index.html`.
- [x] Statisk kontroll av at v1-formatet beholdes (oppgaver, logger, kort, innstillinger).
- [x] Statisk kontroll av Manifest: ingen INTERNET-tillatelse, backup deaktivert.
- [x] Gradle-konfigurasjon: app-ID beholdt, versionCode oppdatert, Compose slått på.
- [x] `python -m unittest discover -s tests -v` – 5/5 statiske kontroller bestått.
- [x] Kotlin-kildefiler skannet med `kotlinc` for parserfeil (ingen syntaksfeil rapportert; Android/Compose-klasser manglet naturlig nok fra kompileringsklassebanen).

## Ikke verifisert (obligatorisk QA før installasjon som oppdatering)

- [ ] Gradle `assembleDebug` (Android SDK/Gradle mangler her).
- [ ] At alle Compose-API- og ikonreferanser løses og appen kompilerer.
- [ ] Installasjon/kaldstart på fysisk Android-telefon.
- [ ] V1 installert → V1.1 installert med **samme signeringsnøkkel** → alt av original historikk/oppgaver/læringskort bevart.
- [ ] Lagringsmigrering på Android 8–15 med alle legacy WebView-kasus.
- [ ] At notat/bottom sheet og tastatur fungerer uten overlapp på 360/393/430 dp.
- [ ] At Android-varsel åpner native I dag, og at varsler fortsatt virker etter reboot/Doze.
- [ ] Redusert bevegelse og TalkBack med stor skrift.
- [ ] Timer, skjermtid og systemtillatelser på faktisk enhet.
- [ ] Motion-FPS / reelle skjermbilder fra native app.

## Kjente avvik fra brief / videre arbeid

- Data er lagret atomisk som lokal, V1-kompatibel JSON, ikke via Room/DataStore. Dette reduserer risikoen ved første migrering, men må vurderes før større datasett.
- Oppdatering fra installert 1.0 forutsetter kompatibel signering. En ny debug-APK med annen nøkkel kan ikke oppdatere en eksisterende signert installasjon.
- NESTE 1.1 har et norsk offline coach-system, ikke server/AI-personalisering eller kalenderkobling.
- Animasjon og skjermdesign er implementert i Compose-kilde, men visuelt QA er nødvendig før det kan kalles premium produksjonskvalitet.
