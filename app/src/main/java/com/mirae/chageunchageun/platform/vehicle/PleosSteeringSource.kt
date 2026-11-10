package com.mirae.chageunchageun.platform.vehicle

import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.port.SteeringSource
import com.mirae.chageunchageun.platform.NOT_IMPLEMENTED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Pleos Vehicle SDK의 조향각 구독(registerSteeringWheelAngle). 담당: 민서
 * 생성자는 AppGraph에 이미 연결돼 있으니 바꾸지 말고 본문만 채웁니다.
 */
class PleosSteeringSource(private val session: PleosVehicleSession) : SteeringSource {
    override val angleDeg: Flow<Signal<Float>> = flowOf(NOT_IMPLEMENTED)
}
