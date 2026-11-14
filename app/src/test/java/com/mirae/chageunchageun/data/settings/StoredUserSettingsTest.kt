package com.mirae.chageunchageun.data.settings

import com.mirae.chageunchageun.domain.settings.AdvisorLevel
import com.mirae.chageunchageun.domain.settings.LocationConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredUserSettingsTest {

    private val store = InMemorySettingsStore()
    private val settings = StoredUserSettings(store)

    /** A second instance over the same store stands in for a process restart. */
    private fun restarted() = StoredUserSettings(store)

    @Test
    fun `defaults on a fresh install`() {
        assertEquals(AdvisorLevel.DETAILED, settings.advisorLevel.value)
        assertEquals(LocationConsent.NOT_ASKED, settings.locationConsent.value)
        assertFalse(settings.firstRunCompleted.value)
    }

    @Test
    fun `level is emitted and survives a restart`() {
        settings.setAdvisorLevel(AdvisorLevel.BRIEF)
        assertEquals(AdvisorLevel.BRIEF, settings.advisorLevel.value)
        assertEquals(AdvisorLevel.BRIEF, restarted().advisorLevel.value)
    }

    @Test
    fun `consent is emitted and survives a restart`() {
        settings.setLocationConsent(LocationConsent.DECLINED)
        assertEquals(LocationConsent.DECLINED, settings.locationConsent.value)
        assertEquals(LocationConsent.DECLINED, restarted().locationConsent.value)
    }

    @Test
    fun `completion is emitted and survives a restart`() {
        settings.completeFirstRun()
        assertTrue(settings.firstRunCompleted.value)
        assertTrue(restarted().firstRunCompleted.value)
    }

    @Test
    fun `one write leaves the other settings alone`() {
        settings.setAdvisorLevel(AdvisorLevel.NORMAL)
        assertEquals(LocationConsent.NOT_ASKED, settings.locationConsent.value)
        assertFalse(settings.firstRunCompleted.value)
    }

    @Test
    fun `reset returns everything to defaults, now and after a restart`() {
        settings.setAdvisorLevel(AdvisorLevel.BRIEF)
        settings.setLocationConsent(LocationConsent.GRANTED)
        settings.completeFirstRun()

        settings.resetFirstRun()

        for (s in listOf(settings, restarted())) {
            assertEquals(AdvisorLevel.DETAILED, s.advisorLevel.value)
            assertEquals(LocationConsent.NOT_ASKED, s.locationConsent.value)
            assertFalse(s.firstRunCompleted.value)
        }
    }

    @Test
    fun `unknown stored values read as defaults`() {
        store.write("advisor_level", "EXPERT")
        store.write("location_consent", "")
        store.write("first_run_completed", "yes")

        val s = restarted()

        assertEquals(AdvisorLevel.DETAILED, s.advisorLevel.value)
        assertEquals(LocationConsent.NOT_ASKED, s.locationConsent.value)
        assertFalse(s.firstRunCompleted.value)
    }
}
