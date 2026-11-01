package no.neste.coach

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the momentary Focusvern overlay defect. */
class FocusWindowPolicyTest {
    @Test fun overlayWindowDoesNotCountAsLeavingInstagram() {
        assertTrue(FocusWindowPolicy.isOurOverlayEvent(
            "no.neste.coach", "no.neste.coach", "android.widget.ScrollView"))
        assertTrue(FocusWindowPolicy.isOurOverlayEvent(
            "no.neste.coach", "no.neste.coach", "android.widget.LinearLayout"))
    }

    @Test fun realNesteActivityCanAlwaysDismissGuard() {
        assertFalse(FocusWindowPolicy.isOurOverlayEvent(
            "no.neste.coach", "no.neste.coach", "no.neste.coach.MainActivity"))
        assertFalse(FocusWindowPolicy.isOurOverlayEvent(
            "no.neste.coach", "no.neste.coach", "no.neste.coach.FocusGuardActivity"))
    }

    @Test fun differentAppIsNotMistakenForAnOverlay() {
        assertFalse(FocusWindowPolicy.isOurOverlayEvent(
            "com.instagram.android", "no.neste.coach", "android.widget.FrameLayout"))
    }

    @Test fun keyguardClosesOverlayButStatusBarDoesNot() {
        assertTrue(FocusWindowPolicy.isKeyguardEvent("com.android.systemui.keyguard.KeyguardView"))
        assertTrue(FocusWindowPolicy.isKeyguardEvent("com.android.systemui.Lockscreen"))
        assertFalse(FocusWindowPolicy.isKeyguardEvent("com.android.systemui.statusbar.StatusBar"))
    }
}
