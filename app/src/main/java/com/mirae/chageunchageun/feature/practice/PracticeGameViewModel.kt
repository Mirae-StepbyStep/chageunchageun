package com.mirae.chageunchageun.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.valueOrNull
import com.mirae.chageunchageun.core.port.ControlsSource
import com.mirae.chageunchageun.core.port.SteeringSource
import com.mirae.chageunchageun.domain.practice.DriveGame
import com.mirae.chageunchageun.domain.practice.GameInput
import com.mirae.chageunchageun.domain.practice.GameState
import com.mirae.chageunchageun.domain.practice.PracticeReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** 게임 진행 단계. 시작 카드에서 준비하고, 카운트다운 뒤에 달립니다. */
sealed interface GamePhase {
    data object Ready : GamePhase
    data class Countdown(val secondsLeft: Float) : GamePhase
    data object Running : GamePhase
}

/**
 * A5 모의 운전. 차량 신호(핸들, 브레이크, 전조등, 와이퍼)를 게임 입력으로 씁니다.
 * 화면이 프레임마다 [tick]을 부릅니다. 화면이 숨겨지면(주행 시작 등) 부르지 않으므로 게임이 멈추고,
 * 화면이 다시 열리면 [reset]으로 시작 카드부터 다시 합니다(터널 한가운데에서 갑자기 이어지지 않게).
 */
class PracticeGameViewModel(controls: ControlsSource, steering: SteeringSource) : ViewModel() {

    /** 차량에서 온 입력. 못 받은 값은 null입니다. */
    val input: StateFlow<GameInput> = combine(
        steering.angleDeg,
        controls.brakePressed,
        controls.lamp(Lamp.LOW_BEAM),
        controls.frontWiper,
    ) { angle, brake, lowBeam, wiper ->
        GameInput(
            steerDeg = angle.valueOrNull(),
            brake = brake.valueOrNull(),
            lowBeam = (lampReading(lowBeam) as? PracticeReading.Switch)?.on,
            wiper = (wiperReading(wiper) as? PracticeReading.Switch)?.on,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GameInput())

    private val _state = MutableStateFlow(DriveGame.start())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _phase = MutableStateFlow<GamePhase>(GamePhase.Ready)
    val phase: StateFlow<GamePhase> = _phase.asStateFlow()

    fun tick(dtSec: Float) {
        when (val phase = _phase.value) {
            GamePhase.Ready -> Unit
            is GamePhase.Countdown -> {
                val left = phase.secondsLeft - dtSec
                _phase.value = if (left <= 0f) GamePhase.Running else GamePhase.Countdown(left)
            }
            GamePhase.Running -> _state.update { DriveGame.step(it, input.value, dtSec) }
        }
    }

    /** 시작 카드의 "시작": 카운트다운 뒤에 달립니다. */
    fun begin() {
        _state.value = DriveGame.start()
        _phase.value = GamePhase.Countdown(COUNTDOWN_SEC)
    }

    /** 시작 카드로 돌아갑니다. */
    fun reset() {
        _state.value = DriveGame.start()
        _phase.value = GamePhase.Ready
    }

    companion object {
        const val COUNTDOWN_SEC = 3f
    }
}
