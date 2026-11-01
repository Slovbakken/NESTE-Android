# NESTE 1.1 – slik får du APK

**Status:** Android-prosjektet er klargjort for APK-bygging. Denne pakken inneholder **ikke en kompilert eller testet APK**. Byggingen må kjøre på en maskin med Android SDK, Gradle og tilgang til avhengigheter fra Google Maven/Maven Central.

## Alternativ 1 — GitHub Actions (enklest uten Android Studio)

1. Opprett et **privat** GitHub-repository, for eksempel `NESTE-Android`.
2. Pakk ut ZIP-filen og last opp **innholdet i `NESTE/`-mappen** til repositoryets rot. Kontroller at `.github/workflows/build-apk.yml` faktisk ble med (skjulte mapper må med).
3. Under **Actions**, velg **NESTE Android 1.1 — bygg APK**. Ved første opprettelse bygger også `push` til `main` automatisk. Du kan velge **Run workflow** dersom det trengs.
4. Når byggejobben har grønn status, åpne den og last ned **NESTE-1.1-Android-APK** under **Artifacts**.
5. Pakk ut artefakt-ZIP-filen. Den inneholder `app-debug.apk`, som du kan overføre til Android og installere.

Hvis jobben er rød, har APK **ikke** blitt bygget. Åpne feilmeldingen i **Kompiler APK** og rett eventuelle kode- eller versjonsfeil. Det er ingen påstand om at dette bygget er verifisert i forkant.

## Alternativ 2 — lokal maskin

- Installer Android Studio med Android SDK Platform 35, Build-Tools 35.0.0 og Java 17.
- Bruk Gradle 8.9 eller Android Studios konfigurerte Gradle.
- Åpne `NESTE/android` i Android Studio og kjør `:app:assembleDebug`.
- APK dukker opp på `NESTE/android/app/build/outputs/apk/debug/app-debug.apk`.
- På maskin med korrekt SDK og `gradle` i PATH kan du også kjøre `./build-apk.sh` fra `NESTE/`.

## Viktig om sikkerhet og data

- En **debug APK** er ment for privat testing, ikke publisering i Google Play.
- APK-en kan bare oppgradere en installert NESTE 1.0 dersom **applicationId og signeringssertifikat er kompatible**. Denne pakken beholder `applicationId`, men debug-signeringsnøkkelen kan være forskjellig mellom byggejobber.
- **Ikke avinstaller NESTE 1.0 for å få 1.1 installert** før du har eksportert en separat sikkerhetskopi. Avinstallering kan slette lokale data, og automatisk migrering er ikke testet på fysisk enhet.
- En vellykket kompilering bekrefter ikke at varsler, migrering eller animasjoner fungerer korrekt på telefonen; dette må testes etterpå.
