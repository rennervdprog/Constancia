package app.constancia

import app.constancia.ui.onboarding.shouldShowOnboarding
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingTest {

    @Test
    fun firstRunShowsOnboarding() {
        assertTrue(shouldShowOnboarding(false, false))
    }

    @Test
    fun completedRunHidesOnboarding() {
        assertFalse(shouldShowOnboarding(true, false))
    }

    @Test
    fun unreadFlagShowsNothingToAvoidFlash() {
        assertFalse(shouldShowOnboarding(null, false))
    }

    @Test
    fun revisitFromMaisShowsOnboardingEvenWhenDone() {
        assertTrue(shouldShowOnboarding(true, true))
    }

    @Test
    fun revisitWhileUnreadAlsoShows() {
        assertTrue(shouldShowOnboarding(null, true))
    }
}
