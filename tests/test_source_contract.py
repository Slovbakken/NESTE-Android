"""Offline source-contract tests. These are NOT APK/emulator tests."""
from pathlib import Path
import json,re,unittest,zipfile
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'android/app/src/main/java/no/neste/coach'
ASSETS=ROOT/'android/app/src/main/assets'

class ContractTests(unittest.TestCase):
    def test_action_catalogue_preserved(self):
        old=(ASSETS/'app.js').read_text()  # archived V1 app bundled for lossless migration
        actions=json.loads((ASSETS/'actions.json').read_text())
        self.assertEqual(len(actions),29)
        self.assertEqual(len({a['id'] for a in actions}),29)
        self.assertEqual({a['area'] for a in actions},{'family','work','training','learn','memory','phone','recovery'})
        for item in actions:
            self.assertIn(item['title'],old)
            self.assertIn(item['description'],old)
    def test_android_native_ui(self):
        ui=(SRC/'MainActivity.kt').read_text()
        self.assertIn('setContent {',ui)
        for marker in ('TodayScreen','PlanScreen','ProgressScreen','MeScreen','FocusScreen','CaptureSheet','Onboarding','ModalBottomSheet','AnimatedContent','BottomNav','ReviewCardSheet'):
            self.assertIn(marker,ui)
        self.assertIn('migrateLegacyWebStorage',ui)
        self.assertIn('file:///android_asset/index.html',ui)
        self.assertIn('neste_state_v1',ui)
        self.assertNotIn('setContentView(webView)',ui)
        self.assertFalse((SRC/'MainActivity.java').exists())
    def test_privacy_and_release(self):
        manifest=(ROOT/'android/app/src/main/AndroidManifest.xml').read_text()
        self.assertNotIn('android.permission.INTERNET',manifest)
        self.assertIn('android:allowBackup="false"',manifest)
        gradle=(ROOT/'android/app/build.gradle').read_text()
        self.assertGreaterEqual(int(re.search(r"versionCode ([0-9]+)",gradle).group(1)), 4)
        self.assertRegex(gradle, r"versionName '1[.]1[.][0-9]+'")
        self.assertIn('compose true',gradle)
    def test_onboarding_exits_and_keeps_data(self):
        ui=(SRC/'MainActivity.kt').read_text()
        storage=(SRC/'CoachStore.kt').read_text()
        self.assertIn('onboardingFinished=true',ui)
        self.assertIn('onFinished()',ui)
        self.assertIn('if(store.setOnboarded(picked.toSet()))',ui)
        self.assertIn('saveError=true',ui)
        self.assertIn('.verticalScroll(rememberScrollState())',ui)
        self.assertIn('onboarded",true',storage)
        self.assertIn('fun setOnboarded(areas: Set<String>): Boolean',storage)

    def test_state_compatibility(self):
        code=(SRC/'CoachStore.kt').read_text()
        for marker in ('AtomicFile','neste_state_native_v1.json','neste_legacy_v1_unmodified.json','importLegacy','importBackup','cards','tasks','logs','reminders','areas','plans','JSONArray','deleteAllData','dueCards','reviewCard','Europe/Oslo'):
            self.assertIn(marker,code)
        self.assertIn('ReminderReceiver',code)
        self.assertIn('original',code.lower())
    def test_no_on_device_network(self):
        for src in SRC.glob('*.kt'):
            self.assertNotIn('HttpURLConnection',src.read_text())
            self.assertNotIn('OkHttpClient',src.read_text())

if __name__=='__main__':unittest.main()
