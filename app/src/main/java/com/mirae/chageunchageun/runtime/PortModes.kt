package com.mirae.chageunchageun.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** 전환할 수 있는 포트. [label]은 디버그 패널에 보입니다. */
enum class PortId(val label: String) {
    DRIVE_STATE("주행 상태"),
    CONTROLS("등화·와이퍼"),
    STEERING("조향각"),
    WARNING("경고"),
    VEHICLE_SPEC("차량 크기"),
    LOCATION("위치·도착"),
    SPEECH_OUT("음성 말하기"),
    SPEECH_IN("음성 듣기"),
}

/** REAL: platform/ 어댑터, FAKE: demo/ 가짜 차량. */
enum class PortMode { REAL, FAKE }

/**
 * 포트마다 실제·가짜 중 무엇을 쓰는지. 앱 실행 중에 바꿀 수 있고, 바꾸면 구독 중인 Flow가 바로 넘어갑니다.
 * 실제 값을 못 받아도 자동으로 가짜로 넘어가지 않습니다. 사람이 직접 고릅니다.
 */
class PortModes(default: PortMode = PortMode.FAKE, real: Set<PortId> = emptySet()) {
    /** [real]에 든 포트는 실제로, 나머지는 [default]로 시작합니다. */
    private val _all = MutableStateFlow(PortId.entries.associateWith { if (it in real) PortMode.REAL else default })
    val all: StateFlow<Map<PortId, PortMode>> = _all.asStateFlow()

    fun get(id: PortId): PortMode = _all.value.getValue(id)

    fun mode(id: PortId): Flow<PortMode> = _all.map { it.getValue(id) }.distinctUntilChanged()

    fun set(id: PortId, mode: PortMode) = _all.update { it + (id to mode) }

    fun setAll(mode: PortMode) = _all.update { PortId.entries.associateWith { mode } }
}

/** 포트 하나의 실제·가짜 구현 쌍. [PortModes]에 따라 하나를 고릅니다. */
class PortSwitch<P>(
    private val modes: PortModes,
    private val id: PortId,
    private val real: P,
    private val fake: P,
) {
    private fun pick(mode: PortMode): P = if (mode == PortMode.REAL) real else fake

    /** 지금 고른 구현. suspend 함수와 일회성 호출에 씁니다. */
    val current: P get() = pick(modes.get(id))

    /** 모드가 바뀌면 고른 구현의 Flow로 갈아탑니다. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> flow(select: (P) -> Flow<T>): Flow<T> = modes.mode(id).flatMapLatest { select(pick(it)) }
}

/**
 * 어댑터가 구현돼 차량(에뮬레이터에서는 Virtual Console) 값을 실제로 받는 포트.
 * 앱은 이 포트들을 실제로 연결한 채 시작합니다. 어댑터를 채운 담당자가 여기에 자기 포트를 추가합니다.
 * 아직 빈 어댑터(차량 크기, 위치·도착, 음성 듣기)는 가짜로 시작합니다.
 */
val CONNECTED_PORTS: Set<PortId> =
    setOf(PortId.DRIVE_STATE, PortId.CONTROLS, PortId.STEERING, PortId.WARNING, PortId.SPEECH_OUT)
