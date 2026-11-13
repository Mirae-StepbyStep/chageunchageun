package com.mirae.chageunchageun.platform.vehicle

import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.WiperState
import com.mirae.chageunchageun.core.port.ControlsSource
import com.mirae.chageunchageun.platform.reason
import com.mirae.chageunchageun.platform.signalFlow
import kotlinx.coroutines.flow.Flow
import ai.pleos.playground.vehicle.constant.control.WiperState as PleosWiperState

/**
 * Pleos Vehicle SDK의 Light, Wiper, Brake 상태 조회와 구독. 담당: 후
 * 생성자는 AppGraph에 이미 연결돼 있으니 바꾸지 말고 본문만 채웁니다.
 *
 * - 읽기만 합니다. 스위치 설정(set) API는 쓰지 않습니다.
 * - 권한: 등화 CAR_LIGHTS, 와이퍼 CAR_WINDOWS, 브레이크 CAR_DRIVE (매니페스트 선언).
 * - 와이퍼는 앞유리 구역만 봅니다.
 */
class PleosControlsSource(private val session: PleosVehicleSession) : ControlsSource {

    private fun light() = session.vehicle().getLight()
    private fun wiper() = session.vehicle().getWiper()
    private fun brake() = session.vehicle().getBrake()

    private val lamps: Map<Lamp, Flow<Signal<LampState>>> = Lamp.entries.associateWith(::lampFlow)

    override fun lamp(lamp: Lamp): Flow<Signal<LampState>> = lamps.getValue(lamp)

    private fun lampFlow(lamp: Lamp): Flow<Signal<LampState>> = signalFlow(
        source = Source.PLEOS,
        getCurrent = { sink ->
            val onComplete = { state: LightState? -> sink.value(state?.let(::lampStateFromPleos)) }
            val onFailed = { e: Exception -> sink.unavailable(e.reason()) }
            when (lamp) {
                Lamp.HIGH_BEAM -> light().getCurrentHighBeamLightState(onComplete, onFailed)
                Lamp.LOW_BEAM -> light().getCurrentLowBeamLightState(onComplete, onFailed)
                Lamp.TAIL -> light().getCurrentTailLampLightState(onComplete, onFailed)
                Lamp.FRONT_FOG -> light().getCurrentFrontFogLightState(onComplete, onFailed)
                Lamp.REAR_FOG -> light().getCurrentRearFogLightState(onComplete, onFailed)
            }
        },
        register = { sink ->
            val listener = object : LightStateListener {
                override fun onLightStateUpdated(state: LightState?) = sink.value(state?.let(::lampStateFromPleos))
                override fun onFailed(e: Exception) = sink.unavailable(e.reason())
            }
            when (lamp) {
                Lamp.HIGH_BEAM -> light().registerHighBeamLightState(listener)
                Lamp.LOW_BEAM -> light().registerLowBeamLightState(listener)
                Lamp.TAIL -> light().registerTailLampLightState(listener)
                Lamp.FRONT_FOG -> light().registerFrontFogLightState(listener)
                Lamp.REAR_FOG -> light().registerRearFogLightState(listener)
            }
            return@signalFlow {
                when (lamp) {
                    Lamp.HIGH_BEAM -> light().unregisterHighBeamLightState(listener)
                    Lamp.LOW_BEAM -> light().unregisterLowBeamLightState(listener)
                    Lamp.TAIL -> light().unregisterTailLampLightState(listener)
                    Lamp.FRONT_FOG -> light().unregisterFrontFogLightState(listener)
                    Lamp.REAR_FOG -> light().unregisterRearFogLightState(listener)
                }
            }
        },
    )

    /** 등록과 해제에 같은 구역 객체를 씁니다. */
    private val frontWindshield = WindowArea(WindowValue.FRONT_WINDSHIELD)

    override val frontWiper: Flow<Signal<WiperState>> = signalFlow(
        source = Source.PLEOS,
        getCurrent = { sink ->
            wiper().getCurrentWiperState(
                frontWindshield,
                { sink.value(it?.let(::wiperStateFromPleos)) },
                { sink.unavailable(it.reason()) },
            )
        },
        register = { sink ->
            val listener = object : WiperStateListener {
                override fun onWiperStateUpdated(state: PleosWiperState?) = sink.value(state?.let(::wiperStateFromPleos))
                override fun onFailed(e: Exception) = sink.unavailable(e.reason())
            }
            wiper().registerWiperState(frontWindshield, listener)
            return@signalFlow { wiper().unregisterWiperState(frontWindshield, listener) }
        },
    )

    override val brakePressed: Flow<Signal<Boolean>> = signalFlow(
        source = Source.PLEOS,
        getCurrent = { sink ->
            brake().getCurrentBrakePedalStatus({ sink.value(it) }, { sink.unavailable(it.reason()) })
        },
        register = { sink ->
            val listener = object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(status: Boolean?) = sink.value(status)
                override fun onFailed(e: Exception) = sink.unavailable(e.reason())
            }
            brake().registerBrakePedalStatus(listener)
            return@signalFlow { brake().unregisterBrakePedalStatus(listener) }
        },
    )
}

/** Pleos 등화 상태를 앱의 [LampState]로 바꿉니다. */
internal fun lampStateFromPleos(state: LightState): LampState = when (state) {
    LightState.OFF -> LampState.OFF
    LightState.ON -> LampState.ON
    LightState.DAYTIME_RUNNING -> LampState.DAYTIME_RUNNING
}

/** Pleos 와이퍼 상태를 앱의 [WiperState]로 바꿉니다. 정비 모드(SERVICE)는 OTHER입니다. */
internal fun wiperStateFromPleos(state: PleosWiperState): WiperState = when (state) {
    PleosWiperState.OFF -> WiperState.OFF
    PleosWiperState.ON -> WiperState.ON
    PleosWiperState.OTHER, PleosWiperState.SERVICE -> WiperState.OTHER
}
