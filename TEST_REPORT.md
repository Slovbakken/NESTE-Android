# NESTE – Verifiseringsrapport

**Dato:** 2026-11-01

## Bestått i Chromium / Playwright

- Responsiv onboarding med to steg.
- Navigasjon mellom I dag, Min plan, Utvikling og Innstillinger.
- Innsjekk av søvn/energi og tilpasning av dagens handling.
- Mindre handling, endringsvalg og registrering av gjennomføring.
- Opprettelse og avhuking av oppgaver med dato.
- Opprettelse, aktiv gjenhenting og repetisjon av læringskort.
- Ukesrapport som bruker faktiske, lokalt registrerte data.
- Eksport-/importformat og simulert prosessomstart med lokalt lagrede data.
- Simulert Android JavascriptInterface: tre innstillinger for daglige varsler, syv datofestede råd og skjermtidsvisning.
- Mobilvisning 393 × 852 px og bred desktopvisning 1380 × 900 px.
- Ingen JavaScript page errors observert i testsesjonen.

## Ikke verifisert i dette miljøet

- APK-kompilering og Android Runtime på ekte telefon. SDK/Gradle var ikke installert og ekstern nedlasting var blokkert.
- Faktisk alarmlevering i Doze, Androids batterisparemodi og på forskjellige produsenter.
- Faktiske skjermtidstall for alle Android-versjoner/enheter.

Android-kilden inkluderer GitHub Actions-bygg som gjør installasjonsfilen tilgjengelig etter vellykket bygg.
