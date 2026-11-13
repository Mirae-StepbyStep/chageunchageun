package com.mirae.chageunchageun.platform.vehicle

import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.port.SteeringSource
import com.mirae.chageunchageun.platform.reason
import com.mirae.chageunchageun.platform.signalFlow
import kotlinx.coroutines.flow.Flow

/**
 * Pleos Vehicle SDK의 조향각 조회와 구독(registerSteeringWheelAngle). 담당: 민서
 * 생성자는 AppGraph에 이미 연결돼 있으니 바꾸지 않습니다.
 * 11/13: A5(후)가 Virtual Console 값을 그대로 받도록 본문을 채웠습니다. B1도 같은 값을 씁니다.
 *
 * - SDK 값의 부호를 뒤집어 내보냅니다. 에뮬레이터에서 SDK가 Virtual Console과 반대 부호로 주기 때문이며
 *   (콘솔 -161 → SDK +161), 뒤집으면 콘솔 값과 같아집니다(왼쪽 음수). 크기는 그대로입니다.
 * - 권한: CAR_DRIVE (매니페스트 선언).
 */
class PleosSteeringSource(private val session: PleosVehicleSession) : SteeringSource {

    private fun steering() = session.vehicle().getSteeringWheel()

    override val angleDeg: Flow<Signal<Float>> = signalFlow(
        source = Source.PLEOS,
        getCurrent = { sink ->
            steering().getCurrentSteeringWheelAngle({ sink.value(angleFromSdk(it)) }, { sink.unavailable(it.reason()) })
        },
        register = { sink ->
            val listener = object : SteeringWheelAngleListener {
                override fun onSteeringWheelAngleUpdated(angle: Float?) = sink.value(angleFromSdk(angle))
                override fun onFailed(e: Exception) = sink.unavailable(e.reason())
            }
            steering().registerSteeringWheelAngle(listener)
            return@signalFlow { steering().unregisterSteeringWheelAngle(listener) } // 등록한 것과 같은 객체로 해제
        },
    )
}

/** SDK 조향각을 포트 규칙(왼쪽 음수, Virtual Console과 같은 부호)으로 바꿉니다. 못 받은 값은 그대로 null입니다. */
internal fun angleFromSdk(sdkDeg: Float?): Float? = sdkDeg?.let { if (it == 0f) 0f else -it }
