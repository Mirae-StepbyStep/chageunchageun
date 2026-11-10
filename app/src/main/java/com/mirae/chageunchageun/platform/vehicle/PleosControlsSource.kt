package com.mirae.chageunchageun.platform.vehicle

import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.WiperState
import com.mirae.chageunchageun.core.port.ControlsSource
import com.mirae.chageunchageun.platform.NOT_IMPLEMENTED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Pleos Vehicle SDK의 Light, Wiper, Brake 상태 구독. 담당: 후
 * 생성자는 AppGraph에 이미 연결돼 있으니 바꾸지 말고 본문만 채웁니다.
 */
class PleosControlsSource(private val session: PleosVehicleSession) : ControlsSource {
    override fun lamp(lamp: Lamp): Flow<Signal<LampState>> = flowOf(NOT_IMPLEMENTED)
    override val frontWiper: Flow<Signal<WiperState>> = flowOf(NOT_IMPLEMENTED)
    override val brakePressed: Flow<Signal<Boolean>> = flowOf(NOT_IMPLEMENTED)
}
