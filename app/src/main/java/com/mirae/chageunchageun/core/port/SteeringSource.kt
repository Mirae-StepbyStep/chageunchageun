package com.mirae.chageunchageun.core.port

import com.mirae.chageunchageun.core.model.Signal
import kotlinx.coroutines.flow.Flow

/** B1 조향각. 담당: 민서 */
interface SteeringSource {
    /** 조향각(도). 부호 방향은 에뮬레이터 시험 전이라 미확인입니다. */
    val angleDeg: Flow<Signal<Float>>
}
