package com.mirae.chageunchageun.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.WiperState
import com.mirae.chageunchageun.core.model.sourceOrNull
import com.mirae.chageunchageun.core.port.ControlsSource
import com.mirae.chageunchageun.core.port.SteeringSource
import com.mirae.chageunchageun.domain.practice.PracticeGuide
import com.mirae.chageunchageun.domain.practice.PracticeItem
import com.mirae.chageunchageun.domain.practice.PracticeReading
import com.mirae.chageunchageun.domain.practice.PracticeSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 안내를 얼마나 자세히 보여 줄지. 저장하지 않아 앱을 끄면 처음 값으로 돌아갑니다. */
enum class AdvisorLevel(val label: String) { DETAILED("자세히"), SIMPLE("간단히") }

/** 화면이 그릴 상태. [session]이 없으면 연습 목록을 보여 줍니다. */
data class PracticeUi(
    val level: AdvisorLevel = AdvisorLevel.DETAILED,
    val completed: Set<PracticeItem> = emptySet(),
    val session: PracticeSession? = null,
    val reading: PracticeReading = PracticeReading.None,
    val source: Source? = null,
) {
    /** 아직 하지 않은 다음 연습. 모두 했으면 없습니다. */
    val nextItem: PracticeItem? get() = PracticeGuide.items.firstOrNull { it !in completed }
}

/** 조작부 하나의 최신 값과 출처. */
private data class Observed(val reading: PracticeReading, val source: Source?)

/**
 * A5 정차 중 조작 연습. 운전자가 직접 조작하면 차량 신호로 확인합니다(앱이 차를 조작하지 않습니다).
 * 주행 시작 시 숨김은 화면의 DrivingGate가 맡고, 진행 상태는 여기 남아 다시 정차하면 이어집니다.
 */
class PracticeViewModel(
    private val controls: ControlsSource,
    private val steering: SteeringSource,
) : ViewModel() {

    private val _ui = MutableStateFlow(PracticeUi())
    val ui: StateFlow<PracticeUi> = _ui.asStateFlow()

    private var watching: Job? = null

    fun setLevel(level: AdvisorLevel) = _ui.update { it.copy(level = level) }

    fun open(item: PracticeItem) {
        watching?.cancel()
        _ui.update { it.copy(session = PracticeGuide.start(item), reading = PracticeReading.None, source = null) }
        watching = viewModelScope.launch {
            observe(item).collect { observed ->
                _ui.update { ui ->
                    val session = ui.session ?: return@update ui
                    ui.copy(reading = observed.reading, source = observed.source)
                        .withSession(PracticeGuide.onReading(session, observed.reading))
                }
            }
        }
    }

    /** 차량 값을 못 받을 때 "직접 확인함", 또는 건너뛸 수 있는 단계의 "건너뛰기". */
    fun confirmManually() = _ui.update { ui ->
        val session = ui.session ?: return@update ui
        ui.withSession(PracticeGuide.confirmManually(session))
    }

    fun backToList() {
        watching?.cancel()
        _ui.update { it.copy(session = null, reading = PracticeReading.None, source = null) }
    }

    fun openNext() {
        _ui.value.nextItem?.let(::open) ?: backToList()
    }

    fun resetAll() {
        backToList()
        _ui.update { it.copy(completed = emptySet()) }
    }

    private fun PracticeUi.withSession(next: PracticeSession): PracticeUi =
        copy(session = next, completed = if (next.isDone) completed + next.item else completed)

    private fun observe(item: PracticeItem): Flow<Observed> = when (item) {
        PracticeItem.LOW_BEAM -> controls.lamp(Lamp.LOW_BEAM).map { Observed(lampReading(it), it.sourceOrNull()) }
        PracticeItem.HIGH_BEAM -> controls.lamp(Lamp.HIGH_BEAM).map { Observed(lampReading(it), it.sourceOrNull()) }
        PracticeItem.WIPER -> controls.frontWiper.map { Observed(wiperReading(it), it.sourceOrNull()) }
        PracticeItem.BRAKE -> controls.brakePressed.map { Observed(brakeReading(it), it.sourceOrNull()) }
        PracticeItem.STEERING -> steering.angleDeg.map { Observed(angleReading(it), it.sourceOrNull()) }
    }
}

// 차량 신호 → 연습 판단용 값. 못 받은 값은 꺼짐이나 0으로 바꾸지 않고 None으로 둡니다.

internal fun lampReading(signal: Signal<LampState>): PracticeReading = when (signal) {
    // 주간주행등은 운전자가 켠 것이 아니라서 꺼짐으로 봅니다.
    is Signal.Value -> PracticeReading.Switch(on = signal.value == LampState.ON)
    is Signal.Unavailable -> PracticeReading.None
}

internal fun wiperReading(signal: Signal<WiperState>): PracticeReading = when (signal) {
    is Signal.Value -> when (signal.value) {
        WiperState.ON -> PracticeReading.Switch(on = true)
        WiperState.OFF -> PracticeReading.Switch(on = false)
        WiperState.OTHER -> PracticeReading.None
    }
    is Signal.Unavailable -> PracticeReading.None
}

internal fun brakeReading(signal: Signal<Boolean>): PracticeReading = when (signal) {
    is Signal.Value -> PracticeReading.Switch(on = signal.value)
    is Signal.Unavailable -> PracticeReading.None
}

internal fun angleReading(signal: Signal<Float>): PracticeReading = when (signal) {
    is Signal.Value -> PracticeReading.Angle(signal.value)
    is Signal.Unavailable -> PracticeReading.None
}
