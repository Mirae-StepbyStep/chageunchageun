package com.mirae.chageunchageun.demo

import com.mirae.chageunchageun.core.model.CarSize
import com.mirae.chageunchageun.core.model.GeoPoint
import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.SpeechPriority
import com.mirae.chageunchageun.core.model.SpeechResult
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.core.model.WiperState
import com.mirae.chageunchageun.core.port.ControlsSource
import com.mirae.chageunchageun.core.port.DriveStateSource
import com.mirae.chageunchageun.core.port.LocationSource
import com.mirae.chageunchageun.core.port.SpeechInput
import com.mirae.chageunchageun.core.port.SpeechOutput
import com.mirae.chageunchageun.core.port.SteeringSource
import com.mirae.chageunchageun.core.port.VehicleSpecSource
import com.mirae.chageunchageun.core.port.WarningSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * 시연·테스트용 가짜 차량. 디버그 패널, 시나리오, 테스트가 여기 값을 바꾸면
 * 아래 Fake 포트들이 그대로 내보냅니다. 모든 값의 출처는 [Source.SCRIPTED]입니다.
 */
class FakeVehicleBus {
    val gear = MutableStateFlow(Gear.P)
    val uxRestricted = MutableStateFlow(false)
    val lamps = MutableStateFlow(Lamp.entries.associateWith { LampState.OFF })
    val frontWiper = MutableStateFlow(WiperState.OFF)
    val brakePressed = MutableStateFlow(false)
    val steeringAngleDeg = MutableStateFlow(0f)
    val warnings = MutableStateFlow<Set<WarningType>>(emptySet())
    val location = MutableStateFlow(GeoPoint(37.5665, 126.9780))
    val arrivals = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val carSize = MutableStateFlow(CarSize(lengthMm = 4_655, widthMm = 1_865, heightMm = 1_665))
    val heard = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** 가짜 음성 출력이 말한 문장. 화면 자막과 테스트 확인에 씁니다. */
    private val _spoken = MutableStateFlow<List<String>>(emptyList())
    val spoken: StateFlow<List<String>> = _spoken.asStateFlow()

    fun setLamp(lamp: Lamp, state: LampState) {
        lamps.value = lamps.value + (lamp to state)
    }

    internal fun recordSpoken(text: String) {
        _spoken.value = (_spoken.value + text).takeLast(20)
    }
}

private fun <T> scripted(value: T): Signal<T> = Signal.Value(value, Source.SCRIPTED)

class FakeDriveStateSource(bus: FakeVehicleBus) : DriveStateSource {
    override val gear: Flow<Signal<Gear>> = bus.gear.map(::scripted)
    override val uxRestricted: Flow<Signal<Boolean>> = bus.uxRestricted.map(::scripted)
}

class FakeControlsSource(private val bus: FakeVehicleBus) : ControlsSource {
    override fun lamp(lamp: Lamp): Flow<Signal<LampState>> =
        bus.lamps.map { it.getValue(lamp) }.distinctUntilChanged().map(::scripted)

    override val frontWiper: Flow<Signal<WiperState>> = bus.frontWiper.map(::scripted)
    override val brakePressed: Flow<Signal<Boolean>> = bus.brakePressed.map(::scripted)
}

class FakeSteeringSource(bus: FakeVehicleBus) : SteeringSource {
    override val angleDeg: Flow<Signal<Float>> = bus.steeringAngleDeg.map(::scripted)
}

class FakeWarningSource(bus: FakeVehicleBus) : WarningSource {
    override val activeWarnings: Flow<Signal<Set<WarningType>>> = bus.warnings.map(::scripted)
}

class FakeVehicleSpecSource(private val bus: FakeVehicleBus) : VehicleSpecSource {
    override suspend fun exteriorSize(): Signal<CarSize> = scripted(bus.carSize.value)
}

class FakeLocationSource(bus: FakeVehicleBus) : LocationSource {
    override val location: Flow<Signal<GeoPoint>> = bus.location.map(::scripted)
    override val arrivals: Flow<String> = bus.arrivals
}

class FakeSpeechOutput(private val bus: FakeVehicleBus) : SpeechOutput {
    override suspend fun speak(text: String, priority: SpeechPriority) = bus.recordSpoken(text)
    override fun stop() = Unit
}

class FakeSpeechInput(private val bus: FakeVehicleBus) : SpeechInput {
    override fun listenOnce(): Flow<SpeechResult> = flow {
        emit(SpeechResult(bus.heard.first(), isFinal = true, source = Source.SCRIPTED))
    }
}
