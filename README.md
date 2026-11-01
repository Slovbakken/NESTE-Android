# NESTE 1.1 – Native Motion

**Android-first personlig handlingscoach · Kotlin / Jetpack Compose · offline**

Dette er en kildekodeleveranse for **NESTE 1.1**, utviklet med utgangspunkt i `NESTE_Android_1.0_Source.zip` og `NESTE_V1.1_Redesign_Brief.md`. Native grensesnitt erstatter 1.0s WebView-baserte hovedskjermer. Den gamle HTML-versjonen ligger fortsatt i `web/` som referanse, og i Androids `assets/` **kun for migrering av 1.0-data**.

## Hva som er implementert i kildekoden

- Compose-skjermene **I dag**, **Plan**, **Utvikling**, **Meg**, med native bunnnavigasjon og midtstilt hurtighandling **Fang opp**.
- Fullskjerms fokusmodus med femminutters timer, `Gjort`, `Delvis gjort`, `Ikke i dag` og mulighet for mindre steg.
- Native `ModalBottomSheet` for notater, dagskapasitet, forklaring, forenkling, alternativ anbefaling, refleksjon, varselinnstillinger og læringskort.
- 29 handlingsforslag med tekst og fokusområder bevart fra 1.0.
- Lokal deterministisk coach med prioritet for egne oppgaver med frist, ukedag, områder, tidligere forslag og kapasitet.
- Planoppgaver med kategori, frist, mulighet for å eie ansvar og fullføring.
- Lagrede læringskort: nytt kort, vise svar, repetisjonsintervaller på 1/3/7/14/30/60 dager.
- Utvikling basert på reelle datoer og registrerte resultater, tydelig forskjell mellom manglende data og ikke gjennomført.
- Android-varsler via eksisterende `ReminderReceiver.java`; omplanlegges etter omstart og appoppdatering; tidspunkt og av/på styres i native skjerm.
- Valgfritt Android-estimat for skjermtid etter eksplisitt systemtilgang.
- JSON-eksport/import via Androids dokumentvelger, og brukerinitiert sletting av lagrede data.
- Native Compose-animert fanebytte, native sheets, animert innhold og haptisk bekreftelse; egen innstilling for redusert bevegelse.

## V1.0 → V1.1: migrering

1. Samme `applicationId` (`no.neste.coach`) som 1.0. **Dette er nødvendig, men ikke tilstrekkelig, for å kunne oppdatere installert app: samme signeringsnøkkel kreves.**
2. Før første native oppstart brukes en *skjult, kortvarig* WebView som laster nøyaktig `file:///android_asset/index.html` for å hente `localStorage['neste_state_v1']`.
3. V1 JSON kontrolleres, lagres uendret i privat lokal backup og kopieres atomisk til `neste_state_native_v1.json`.
4. Ukjente V1-felter og historikk, inkludert originale 1–5-innsjekker og repetisjonskort, forblir i JSON-datamodellen.
5. Koden merker ikke mislykket migrering som fullført, og sletter ikke det gamle WebView-lageret. JSON-import er fortsatt tilgjengelig.
6. Sletting er brukerinitiert og rydder både native data, migreringskopier og appens WebView-lager.

**VIKTIG:** Migreringen må QA-testes på telefon med en faktisk installert 1.0 og brukerdata. Det finnes ingen verifisert reell migrering fra dette miljøet. Ikke installer som oppdatering uten sikkerhetskopi av originale data.

## Signert Android-oppdatering (1.1.1 og nyere)

Produksjonsinstallasjoner skal bruke **signert release APK**, ikke den flyktig signerte debug-APK-en fra GitHub Actions.
Byggejobben `.github/workflows/build-apk.yml` lager en verifisert `NESTE-1.1.1-SIGNED-APK` bare når GitHub repository secrets `NESTE_KEYSTORE_BASE64` og `NESTE_SIGNING_PASSWORD` finnes. De to verdiene må forbli hemmelige, og den samme signeringsnøkkelen må gjenbrukes for alle fremtidige utgaver. Se `OPPDATERINGER_OG_SIGNERING.md`.

**Engangsovergang:** Den aller første 1.1.0 debug-APK-en hadde en ikke-bevart signeringsnøkkel. Den kan ikke overskrives med en APK signert med den nye permanente nøkkelen; ta vare på brukerdata før eventuell avinstallering. Etter installasjon av permanent signert 1.1.1 kan nyere signerte versjoner med samme `applicationId` og stigende `versionCode` installeres oppå eksisterende app.

## Bygg debug-APK

Dette prosjektet krever Android SDK 35, JDK 17, Gradle 8.9, AGP 8.7.3 og Kotlin 2.0.21 med Compose.

1. Åpne `android/` i Android Studio og synkroniser avhengighetene.
2. Bygg med `gradle --no-daemon assembleDebug` fra `android/` (eller Android Studios **Build APK(s)**).
3. Finn `android/app/build/outputs/apk/debug/app-debug.apk`.

Du kan også kjøre `.github/workflows/build-apk.yml` i et GitHub-repository med `NESTE/`-innholdet i repo-roten; workflowen installerer SDK og Gradle og bygger APK som artifact.

> **Verifikasjonsstatus:** Fem statiske kildekodetester er bestått. Android-kompilering, installasjon, skjermbilder fra native UI, varsler på enhet, migrering fra V1 og reelle FPS/motion-målinger er **ikke verifisert**. Dette er kildekode som må bygges og QA-testes, ikke en ferdig testet APK.

## Design

Se `NESTE_V1.1_Redesign_Brief.md`. Farger: bakgrunn `#F6F5F0`, hovedtekst `#202924`, primærknapp `#213D32`, sage `#E5EBE4`, linje `#DCE1D8`. Ingen konfetti, streak-straff, kunstige AI-etiketter eller annonser.

## Struktur

```text
NESTE/
  android/
    app/src/main/java/no/neste/coach/
      MainActivity.kt       – Native Compose UI, Android-integrasjon, migrering
      CoachStore.kt         – Regelmotor, lagring, oppgaver, dataeksport og læringskort
      ReminderReceiver.java – Daglige varsler og oppstarts-rescheduling
    app/src/main/assets/
      actions.json          – 29 handlingsforslag bevart fra 1.0
      index.html, app.js    – Gammel webapp for sikker migrering (ikke hoved-UI)
  web/                      – Arkivert fungerende 1.0-webvisning
  tests/test_source_contract.py
  TEST_REPORT_1.1.md
  NESTE_V1.1_Redesign_Brief.md
  .github/workflows/build-apk.yml
```

**Produktgrense:** NESTE er en privat, regelstyrt selvhjelpscoach, ikke en medisinsk vurdering eller en generativ AI-tjeneste. Ingen `INTERNET`-tillatelse er deklarert.
