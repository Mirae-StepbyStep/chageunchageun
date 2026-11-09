package com.example.chaguenchaguen.core.port

import com.example.chaguenchaguen.core.model.CarSize
import com.example.chaguenchaguen.core.model.GeoPoint
import com.example.chaguenchaguen.core.model.Gear
import com.example.chaguenchaguen.core.model.MoveDirection
import com.example.chaguenchaguen.core.model.SeatAxis
import com.example.chaguenchaguen.core.model.SeatRange
import com.example.chaguenchaguen.core.model.Signal
import com.example.chaguenchaguen.core.model.SpeechPriority
import com.example.chaguenchaguen.core.model.SpeechResult
import com.example.chaguenchaguen.core.model.TurnSignal
import com.example.chaguenchaguen.core.model.WarningType
import kotlinx.coroutines.flow.Flow

/*
 * 앱이 바깥 세계(차량, 위치, 음성)를 보는 유일한 창구입니다.
 * - 구현은 platform/(실제)과 demo/(가짜) 두 벌이고, runtime/AppGraph가 고릅니다.
 * - feature/와 domain/은 이 인터페이스만 씁니다.
 * - 이 파일을 바꾸려면 PR 리뷰를 받습니다.
 */

/** 주행 판단과 D1 기록에 쓰는 신호. 담당: 후(어댑터), 수현·재균(사용) */
interface DrivingSignals {
    val gear: Flow<Signal<Gear>>
    val speedMps: Flow<Signal<Float>>
    val brakePressed: Flow<Signal<Boolean>>
    val turnSignal: Flow<Signal<TurnSignal>>
    val steeringAngleDeg: Flow<Signal<Float>>

    /** CarUxRestrictions의 주행 규제 여부. true면 상세 화면을 숨깁니다. */
    val uxRestricted: Flow<Signal<Boolean>>
}

/** C1 경고. 켜져 있는 경고의 집합을 내보냅니다. 담당: 재균 */
interface WarningSource {
    val activeWarnings: Flow<Signal<Set<WarningType>>>
}

/** A1 시트. 목표 위치 지정이 없으므로 위치를 보며 STOP합니다. 담당: 후 */
interface SeatController {
    /** 축별 지원 범위. 지원하지 않는 축은 Unavailable. */
    suspend fun range(axis: SeatAxis): Signal<SeatRange>

    fun position(axis: SeatAxis): Flow<Signal<Int>>

    /** 실패하면 예외를 던집니다. 요청 완료가 실제 이동을 뜻하지는 않습니다. */
    suspend fun move(axis: SeatAxis, direction: MoveDirection)
}

/** B1 차량 크기. 담당: 민서 */
interface VehicleSpecSource {
    suspend fun exteriorSize(): Signal<CarSize>
}

/** B1 위치와 도착. 담당: 민서 */
interface LocationSource {
    val location: Flow<Signal<GeoPoint>>

    /** 목적지 도착 이벤트. 값은 도착지 이름(모르면 빈 문자열). */
    val arrivals: Flow<String>
}

/** 음성 출력(C1, B1, E2). 담당: E2 담당 */
interface SpeechOutput {
    /** 재생이 끝나면 반환합니다. 더 높은 우선순위가 오면 끊길 수 있습니다. */
    suspend fun speak(text: String, priority: SpeechPriority = SpeechPriority.NORMAL)

    fun stop()
}

/** 음성 입력(E2). 담당: E2 담당 */
interface SpeechInput {
    /** 한 번 듣기. 최종 결과를 내보내고 끝납니다. */
    fun listenOnce(): Flow<SpeechResult>
}
