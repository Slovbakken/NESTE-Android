# NESTE – oppdaterbar APK (versjon 1.1.1 og nyere)

**Viktig:** APK-en fra 1.1.0 (GitHub Actions 41324517962) ble signert med en **midlertidig debug-keystore** som ikke ble lagret. Siden vi ikke har den gamle private nøkkelen, kan Android ikke installere en APK signert med en ny nøkkel oppå den installasjonen. Det er en Android-sikkerhetsregel. Vi kan ikke omgå den uten tilgang til gammel privat nøkkel. Dette krever **én engangsovergang**, ikke en gjentakende prosedyre.

## Ny permanent signering

En separat privat signeringsnøkkel er opprettet og utlevert som en privat sikkerhetskopi. **Ikke last opp nøkkelen eller tekstfilene i repositoryet.** Repositoryet er for tiden offentlig.

1. Åpne `https://github.com/Slovbakken/NESTE-Android/settings/secrets/actions`.
2. Opprett repository secret `NESTE_KEYSTORE_BASE64` med **hele** innholdet i `NESTE_KEYSTORE_BASE64.txt`.
3. Opprett repository secret `NESTE_SIGNING_PASSWORD` med innholdet i `NESTE_KEYSTORE_PASSWORD.txt`.
4. Åpne **Actions** → **NESTE Android – build and signed APK** → **Run workflow**. 
5. Når jobben er ferdig: Last ned artifact `NESTE-1.1.1-SIGNED-APK`. Filen inni heter `app-release.apk`.
6. Første gang: Ta backup av eventuell gammel brukerdata, avinstaller bare den tidligere 1.1-debug-appen, installer `app-release.apk`. **Alle senere bygg som bruker de samme to hemmelighetene, samme applicationId og økende versionCode kan installeres direkte over den installerte appen** uten sletting.

### Viktige krav

- Uendret `applicationId = no.neste.coach`.
- `versionCode` økes for hver oppdatering.
- Samme `neste-release.jks` og passord må brukes på hver release.
- Bruk `app-release.apk` fra signert release-artifact, **ikke** `app-debug.apk`.
- Ikke regenerer signeringsnøkkelen. Oppbevar en sikkerhetskopi utenfor GitHub.

Hvis secrets mangler, bygges koden for kvalitetskontroll, men Actions laster **ikke** opp en usignert eller flyktig debug-APK som oppdateringsfil.

## Oppstartsfeil løst i 1.1.1

Første oppstartsskjerm hadde en for høy Column som presset Fortsett-knappen under navigasjonsområdet. Nå er valgene rullbare og Fortsett ligger i en fast bunnlinje som tar hensyn til Androids navigasjonsfelt. Utvalget bevares også ved rotasjon.
