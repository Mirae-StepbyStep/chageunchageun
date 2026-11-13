package com.mirae.chageunchageun.core.port

import com.mirae.chageunchageun.core.model.Signal
import kotlinx.coroutines.flow.Flow

/** B1 조향각. 담당: 민서 */
interface SteeringSource {
    /**
     * 조향각(도). 왼쪽이 음수, 오른쪽이 양수입니다(Virtual Console의 Steering Angle과 같은 부호).
     * SDK는 부호를 반대로 주므로(에뮬레이터 v2.1.0에서 콘솔 -161 → SDK +161, 11/13 확인) 어댑터가 뒤집어 맞춥니다.
     * 실제 차량에서의 부호는 확인하지 못했습니다.
     */
    val angleDeg: Flow<Signal<Float>>
}
