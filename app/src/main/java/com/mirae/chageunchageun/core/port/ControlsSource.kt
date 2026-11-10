package com.mirae.chageunchageun.core.port

import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.WiperState
import kotlinx.coroutines.flow.Flow

/**
 * A5 조작 연습: 운전자가 조작한 결과(실제 상태)를 봅니다. 담당: 후
 *
 * 스위치 단계(예: 와이퍼 간헐 2단)는 SDK에 구독이 없어 여기에 없습니다.
 */
interface ControlsSource {
    fun lamp(lamp: Lamp): Flow<Signal<LampState>>

    /** 앞유리 와이퍼. */
    val frontWiper: Flow<Signal<WiperState>>

    val brakePressed: Flow<Signal<Boolean>>
}
