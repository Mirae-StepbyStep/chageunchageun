package com.mirae.chageunchageun.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirae.chageunchageun.core.model.DrivePhase
import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.sourceOrNull
import com.mirae.chageunchageun.core.model.valueOrNull
import com.mirae.chageunchageun.core.port.ControlsSource
import com.mirae.chageunchageun.core.port.DriveStateSource
import com.mirae.chageunchageun.runtime.DriveRuntime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 화면이 그릴 상태. SDK 타입 없이 화면에 필요한 값만 담습니다. */
data class HomeUi(
    val phase: DrivePhase = DrivePhase.DRIVING,
    val gear: Gear? = null,
    val gearSource: Source? = null,
    val highBeam: LampState? = null,
    val highBeamSource: Source? = null,
)

/**
 * 예시 기능: 포트 → ViewModel → 화면.
 * 생성자로 포트만 받으므로 JVM 테스트에서는 Fake 포트를 넣으면 됩니다.
 */
class HomeViewModel(driveState: DriveStateSource, controls: ControlsSource, runtime: DriveRuntime) : ViewModel() {

    val ui: StateFlow<HomeUi> = combine(
        runtime.phase,
        driveState.gear,
        controls.lamp(Lamp.HIGH_BEAM),
    ) { phase, gear, highBeam ->
        HomeUi(
            phase = phase,
            gear = gear.valueOrNull(),
            gearSource = gear.sourceOrNull(),
            highBeam = highBeam.valueOrNull(),
            highBeamSource = highBeam.sourceOrNull(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}
