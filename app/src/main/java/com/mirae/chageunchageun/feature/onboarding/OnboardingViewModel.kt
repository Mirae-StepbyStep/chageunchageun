package com.mirae.chageunchageun.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirae.chageunchageun.domain.settings.AdvisorLevel
import com.mirae.chageunchageun.domain.settings.LocationConsent
import com.mirae.chageunchageun.domain.settings.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class OnboardingStep { LOCATION, LEVEL, DONE }

/** What the screen draws. No Android types. */
data class OnboardingUi(
    /** Driving or UX-restricted: the screen shows one sentence and nothing else. */
    val restricted: Boolean,
    /** First run already completed: both settings are shown as sections and save on change. */
    val editMode: Boolean,
    val step: OnboardingStep,
    val level: AdvisorLevel,
    val consent: LocationConsent,
    /** In-app consent is given but the Android location permission is not granted. */
    val permissionOff: Boolean,
    /** The screen should launch the Android permission request now. Never true while restricted. */
    val launchPermissionRequest: Boolean,
)

/**
 * First-run setup: location consent, then advisor level. Settings are saved as they are chosen;
 * completion is saved when the user leaves the last step. After completion the same screen is the settings editor.
 *
 * The step and a pending permission request live here, so they survive a driving period and tab switches.
 * The Android permission itself is requested and checked by the screen, which reports back.
 */
class OnboardingViewModel(
    private val settings: UserSettings,
    private val canShowDetail: StateFlow<Boolean>,
) : ViewModel() {

    private enum class Request { NONE, WANTED, LAUNCHED }

    private data class Local(
        val step: OnboardingStep = OnboardingStep.LOCATION,
        val request: Request = Request.NONE,
        /** null until the screen has checked. */
        val permissionGranted: Boolean? = null,
    )

    private val local = MutableStateFlow(Local())

    val ui: StateFlow<OnboardingUi> = combine(
        canShowDetail,
        settings.firstRunCompleted,
        settings.advisorLevel,
        settings.locationConsent,
        local,
        ::build,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), snapshot())

    private fun snapshot() = build(
        canShowDetail.value,
        settings.firstRunCompleted.value,
        settings.advisorLevel.value,
        settings.locationConsent.value,
        local.value,
    )

    private fun build(
        canShow: Boolean,
        completed: Boolean,
        level: AdvisorLevel,
        consent: LocationConsent,
        local: Local,
    ) = OnboardingUi(
        restricted = !canShow,
        editMode = completed,
        step = local.step,
        level = level,
        consent = consent,
        permissionOff = consent == LocationConsent.GRANTED && local.permissionGranted == false,
        launchPermissionRequest = canShow && local.request == Request.WANTED,
    )

    /** 동의해요. Saves consent, then asks the screen for the Android permission. The step advances on the result. */
    fun agreeToLocation() {
        if (local.value.request != Request.NONE) return
        settings.setLocationConsent(LocationConsent.GRANTED)
        local.update { it.copy(request = Request.WANTED) }
    }

    /** 나중에 할게요. */
    fun declineLocation() {
        settings.setLocationConsent(LocationConsent.DECLINED)
        local.update { it.copy(request = Request.NONE, step = afterLocation(it.step)) }
    }

    /** The screen calls this right before launching the request, so it is launched once. */
    fun onPermissionRequestLaunched() {
        local.update { if (it.request == Request.WANTED) it.copy(request = Request.LAUNCHED) else it }
    }

    /** Result of the Android request. Denied is not retried; the flow continues either way. */
    fun onPermissionResult(granted: Boolean) {
        local.update { it.copy(request = Request.NONE, permissionGranted = granted, step = afterLocation(it.step)) }
    }

    /**
     * Current Android permission state, checked by the screen when it opens.
     * A request whose result never arrived (the screen left mid-request) is dropped here, so the user can ask again.
     */
    fun onPermissionChecked(granted: Boolean) {
        local.update {
            it.copy(permissionGranted = granted, request = if (it.request == Request.LAUNCHED) Request.NONE else it.request)
        }
    }

    fun selectLevel(level: AdvisorLevel) = settings.setAdvisorLevel(level)

    fun next() {
        local.update { if (it.step == OnboardingStep.LEVEL) it.copy(step = OnboardingStep.DONE) else it }
    }

    fun back() {
        local.update {
            when (it.step) {
                OnboardingStep.LOCATION -> it
                OnboardingStep.LEVEL -> it.copy(step = OnboardingStep.LOCATION)
                OnboardingStep.DONE -> it.copy(step = OnboardingStep.LEVEL)
            }
        }
    }

    /** Leaving the last step through either button. This is the only place completion is saved. */
    fun finish() {
        if (local.value.step != OnboardingStep.DONE) return
        settings.completeFirstRun()
        local.update { it.copy(step = OnboardingStep.LOCATION) }
    }

    /** Debug only: back to a fresh install. */
    fun resetFirstRun() {
        settings.resetFirstRun()
        local.update { Local(permissionGranted = it.permissionGranted) }
    }

    private fun afterLocation(step: OnboardingStep): OnboardingStep =
        if (step == OnboardingStep.LOCATION && !settings.firstRunCompleted.value) OnboardingStep.LEVEL else step
}
