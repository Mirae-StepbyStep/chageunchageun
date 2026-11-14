package com.mirae.chageunchageun.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirae.chageunchageun.core.model.DrivePhase
import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.NOT_YET
import com.mirae.chageunchageun.core.model.Severity
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.sourceOrNull
import com.mirae.chageunchageun.core.model.valueOrNull
import com.mirae.chageunchageun.core.port.ControlsSource
import com.mirae.chageunchageun.core.port.DriveStateSource
import com.mirae.chageunchageun.core.port.WarningSource
import com.mirae.chageunchageun.domain.guidance.WarningGuidance
import com.mirae.chageunchageun.domain.guidance.WarningGuide
import com.mirae.chageunchageun.domain.settings.AdvisorLevel
import com.mirae.chageunchageun.domain.settings.LocationConsent
import com.mirae.chageunchageun.domain.settings.UserSettingsReader
import com.mirae.chageunchageun.runtime.DriveRuntime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** 화면이 그릴 상태. SDK 타입 없이 화면에 필요한 값만 담습니다. */
data class HomeUi(
    val phase: DrivePhase = DrivePhase.DRIVING,
    val gear: Gear? = null,
    val gearSource: Source? = null,
    val highBeam: LampState? = null,
    val highBeamSource: Source? = null,
    /** Parked and not UX-restricted. False shows the driving view. */
    val canShowDetail: Boolean = false,
    /** Active C1 warnings, most severe first. Empty when none or unknown. */
    val warnings: List<WarningGuide> = emptyList(),
    val level: AdvisorLevel = AdvisorLevel.DETAILED,
    val consent: LocationConsent = LocationConsent.NOT_ASKED,
    val firstRunCompleted: Boolean = true,
) {
    /** The warning card while parked. */
    val parkedWarning: WarningGuide? get() = warnings.firstOrNull()

    /** The warning card while driving: same rule as C1's driving banner (INFO is not shown). */
    val drivingWarning: WarningGuide? get() = warnings.firstOrNull { it.severity != Severity.INFO }
}

enum class LocationStatus { ON, OFF, PERMISSION_OFF }

/** In-app consent plus the Android permission, as the settings summary shows it. */
fun locationStatus(consent: LocationConsent, permissionGranted: Boolean): LocationStatus = when {
    consent != LocationConsent.GRANTED -> LocationStatus.OFF
    !permissionGranted -> LocationStatus.PERMISSION_OFF
    else -> LocationStatus.ON
}

/**
 * 예시 기능: 포트 → ViewModel → 화면.
 * 생성자로 포트만 받으므로 JVM 테스트에서는 Fake 포트를 넣으면 됩니다.
 */
class HomeViewModel(
    driveState: DriveStateSource,
    controls: ControlsSource,
    runtime: DriveRuntime,
    // Optional so the three-port example above still stands on its own (and in tests).
    warnings: WarningSource? = null,
    settings: UserSettingsReader? = null,
) : ViewModel() {

    private val vehicle = combine(
        runtime.phase,
        driveState.gear,
        controls.lamp(Lamp.HIGH_BEAM),
        runtime.canShowDetail,
    ) { phase, gear, highBeam, canShowDetail ->
        HomeUi(
            phase = phase,
            gear = gear.valueOrNull(),
            gearSource = gear.sourceOrNull(),
            highBeam = highBeam.valueOrNull(),
            highBeamSource = highBeam.sourceOrNull(),
            canShowDetail = canShowDetail,
        )
    }

    private val activeWarnings = warnings?.activeWarnings ?: flowOf(NOT_YET)

    private val withWarnings = combine(vehicle, activeWarnings) { ui, signal ->
        val types = signal.valueOrNull().orEmpty()
        ui.copy(warnings = WarningGuidance.ordered(WarningGuidance.relevant(types, ui.phase)))
    }

    val ui: StateFlow<HomeUi> = if (settings == null) {
        withWarnings
    } else {
        combine(
            withWarnings,
            settings.advisorLevel,
            settings.locationConsent,
            settings.firstRunCompleted,
        ) { ui, level, consent, completed ->
            ui.copy(level = level, consent = consent, firstRunCompleted = completed)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}
