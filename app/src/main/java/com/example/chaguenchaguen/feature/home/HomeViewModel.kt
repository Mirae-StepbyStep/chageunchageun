package com.example.chaguenchaguen.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chaguenchaguen.core.model.DrivePhase
import com.example.chaguenchaguen.core.model.Gear
import com.example.chaguenchaguen.core.model.Source
import com.example.chaguenchaguen.core.model.TurnSignal
import com.example.chaguenchaguen.core.model.sourceOrNull
import com.example.chaguenchaguen.core.model.valueOrNull
import com.example.chaguenchaguen.core.port.DrivingSignals
import com.example.chaguenchaguen.runtime.DriveRuntime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 화면이 그릴 상태. SDK 타입 없이 화면에 필요한 값만 담습니다. */
data class HomeUi(
    val phase: DrivePhase = DrivePhase.DRIVING,
    val gear: Gear? = null,
    val gearSource: Source? = null,
    val turnSignal: TurnSignal? = null,
    val turnSignalSource: Source? = null,
    val speedKmh: Int? = null,
)

/**
 * 예시 기능: 포트 → ViewModel → 화면.
 * 생성자로 포트만 받으므로 JVM 테스트에서는 Fake 포트를 넣으면 됩니다.
 */
class HomeViewModel(signals: DrivingSignals, runtime: DriveRuntime) : ViewModel() {

    val ui: StateFlow<HomeUi> = combine(
        runtime.phase,
        signals.gear,
        signals.turnSignal,
        signals.speedMps,
    ) { phase, gear, turn, speed ->
        HomeUi(
            phase = phase,
            gear = gear.valueOrNull(),
            gearSource = gear.sourceOrNull(),
            turnSignal = turn.valueOrNull(),
            turnSignalSource = turn.sourceOrNull(),
            speedKmh = speed.valueOrNull()?.let { (it * 3.6f).toInt() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}
