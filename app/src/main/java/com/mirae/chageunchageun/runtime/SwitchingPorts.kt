package com.mirae.chageunchageun.runtime

import com.mirae.chageunchageun.core.model.CarSize
import com.mirae.chageunchageun.core.model.GeoPoint
import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.Signal
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

/*
 * 포트별 전환 래퍼. 기능 코드는 이 객체를 포트로 받으므로 실제·가짜 전환을 모릅니다.
 * 포트에 멤버를 추가하면 여기에도 한 줄 추가합니다.
 */

class SwitchingDriveStateSource(private val sw: PortSwitch<DriveStateSource>) : DriveStateSource {
    override val gear: Flow<Signal<Gear>> = sw.flow { it.gear }
    override val uxRestricted: Flow<Signal<Boolean>> = sw.flow { it.uxRestricted }
}

class SwitchingControlsSource(private val sw: PortSwitch<ControlsSource>) : ControlsSource {
    override fun lamp(lamp: Lamp): Flow<Signal<LampState>> = sw.flow { it.lamp(lamp) }
    override val frontWiper: Flow<Signal<WiperState>> = sw.flow { it.frontWiper }
    override val brakePressed: Flow<Signal<Boolean>> = sw.flow { it.brakePressed }
}

class SwitchingSteeringSource(private val sw: PortSwitch<SteeringSource>) : SteeringSource {
    override val angleDeg: Flow<Signal<Float>> = sw.flow { it.angleDeg }
}

class SwitchingWarningSource(private val sw: PortSwitch<WarningSource>) : WarningSource {
    override val activeWarnings: Flow<Signal<Set<WarningType>>> = sw.flow { it.activeWarnings }
}

class SwitchingVehicleSpecSource(private val sw: PortSwitch<VehicleSpecSource>) : VehicleSpecSource {
    override suspend fun exteriorSize(): Signal<CarSize> = sw.current.exteriorSize()
}

class SwitchingLocationSource(private val sw: PortSwitch<LocationSource>) : LocationSource {
    override val location: Flow<Signal<GeoPoint>> = sw.flow { it.location }
    override val arrivals: Flow<String> = sw.flow { it.arrivals }
}

class SwitchingSpeechOutput(private val sw: PortSwitch<SpeechOutput>) : SpeechOutput {
    override suspend fun speak(text: String, priority: SpeechPriority) = sw.current.speak(text, priority)
    override fun stop() = sw.current.stop()
}

class SwitchingSpeechInput(private val sw: PortSwitch<SpeechInput>) : SpeechInput {
    override fun listenOnce(): Flow<SpeechResult> = sw.current.listenOnce()
}
