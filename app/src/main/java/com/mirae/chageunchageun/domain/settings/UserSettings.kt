package com.mirae.chageunchageun.domain.settings

import kotlinx.coroutines.flow.StateFlow

/** How detailed guidance starts out. Starting point for E1 and A5; C1 warnings are never shortened by it. */
enum class AdvisorLevel { DETAILED, NORMAL, BRIEF }

/** In-app consent only. The Android location permission is checked at runtime and is not stored. */
enum class LocationConsent { NOT_ASKED, GRANTED, DECLINED }

/** Read side of the app settings. Features that only need the values (E1, A5, B1, B2) depend on this. */
interface UserSettingsReader {
    val advisorLevel: StateFlow<AdvisorLevel>
    val locationConsent: StateFlow<LocationConsent>
    val firstRunCompleted: StateFlow<Boolean>
}

/** Write side. Used only by first-run and Home. */
interface UserSettings : UserSettingsReader {
    fun setAdvisorLevel(level: AdvisorLevel)
    fun setLocationConsent(consent: LocationConsent)
    fun completeFirstRun()

    /** Debug and demo: back to a fresh install (not completed, consent not asked, level DETAILED). */
    fun resetFirstRun()
}
