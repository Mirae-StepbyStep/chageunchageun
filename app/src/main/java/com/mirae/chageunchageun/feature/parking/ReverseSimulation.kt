package com.mirae.chageunchageun.feature.parking

import kotlin.math.abs
import kotlin.math.min

/*
 * 개발자용 가상 후진 시뮬레이션. 순수 Kotlin(JVM 테스트 가능).
 *
 * 실제 차량을 움직이는 기능이 아니라 화면 속 가상 차량의 진행을 제어합니다.
 * 차량 위치(pose)는 이 객체 하나만 바꿉니다. 화면은 일정 간격으로 [tick]을 부르고,
 * 버튼은 [start]/[pause]/[resume]/[reset]/[nudge]를 바로 부릅니다(별도 코루틴 없음).
 *
 * 그래서
 * - 시작을 여러 번 눌러도 이동이 겹치거나 속도가 누적되지 않고,
 * - 일시정지 뒤에는 어떤 tick도 위치를 바꾸지 않으며,
 * - tick 간격이 길어져도 한 번에 [MAX_TICK_SEC]까지만 움직여 재개 때 튀지 않습니다.
 */

/** 기본 가상 후진 속도(km/h). 관찰용 시뮬레이션 값이며 실제 주차 권장 속도가 아닙니다. */
const val SIM_DEFAULT_SPEED_KMH = 0.5f
const val SIM_MIN_SPEED_KMH = 0.1f
const val SIM_MAX_SPEED_KMH = 2.0f

/** tick 한 번에 반영하는 최대 시간(초). 앱이 잠깐 멈췄다 돌아와도 차가 순간 이동하지 않게. */
const val MAX_TICK_SEC = 0.1f

/** "목표 조향각 도달 후 이동"에서 목표가 이만큼 바뀌면 다시 멈춥니다(도). */
const val TARGET_CHANGE_PAUSE_DEG = 90f

fun kmhToMps(kmh: Float): Float = kmh / 3.6f

enum class SimState {
    /** 시뮬레이션을 쓰지 않음. 외부 속도 신호(시나리오 등)가 있으면 그것으로 움직입니다. */
    IDLE,
    RUNNING,
    PAUSED,
    /** "목표 조향각 도달 후 이동" 옵션 때문에 멈춤. 목표에 맞추고 재개해야 움직입니다. */
    WAITING_STEER,
    DONE,
}

data class SimSnapshot(
    val state: SimState,
    val pose: Pose,
    val speedKmh: Float,
    val travelledM: Float,
    val waitForSteer: Boolean,
    val note: String,
) {
    /** 지금 가상 차량에 걸린 속도(m/s). FakeVehicleBus 속도 신호와 같아야 합니다. */
    val appliedSpeedMps: Float get() = if (state == SimState.RUNNING) kmhToMps(speedKmh) else 0f
}

class ReverseSimulation(
    private val geometry: () -> VehicleGeometry,
    private val startPose: Pose = Pose(),
) {
    var state: SimState = SimState.IDLE
        private set
    var pose: Pose = startPose
        private set
    var speedKmh: Float = SIM_DEFAULT_SPEED_KMH
        private set
    var travelledM: Float = 0f
        private set
    var waitForSteer: Boolean = false
        private set
    var note: String = ""
        private set

    /** 재개할 때의 목표 조향각. 진행 중 목표가 크게 바뀌었는지 비교합니다. */
    private var targetAtResume: Float? = null

    fun snapshot() = SimSnapshot(state, pose, speedKmh, travelledM, waitForSteer, note)

    fun setSpeedKmh(kmh: Float) {
        speedKmh = kmh.coerceIn(SIM_MIN_SPEED_KMH, SIM_MAX_SPEED_KMH)
    }

    fun setWaitForSteer(on: Boolean) {
        waitForSteer = on
        if (!on && state == SimState.WAITING_STEER) {
            state = SimState.PAUSED
            note = "일시정지"
        }
    }

    /** 현재 위치에서 저속 후진 시작. 이미 진행 중이면 아무 일도 하지 않습니다. */
    fun start(currentTargetDeg: Float?) {
        if (state == SimState.RUNNING || state == SimState.DONE) return
        state = SimState.RUNNING
        targetAtResume = currentTargetDeg
        note = "진행 중"
    }

    fun pause(reason: String = "일시정지") {
        if (state != SimState.RUNNING) return
        state = SimState.PAUSED
        note = reason
    }

    /**
     * 일시정지한 위치에서 다시 진행. "목표 조향각 도달 후 이동"이 켜져 있으면
     * 현재 핸들이 목표 구간 안일 때만 재개합니다.
     */
    fun resume(steeringDeg: Float, targetDeg: Float?): Boolean {
        if (state != SimState.PAUSED && state != SimState.WAITING_STEER) return false
        if (waitForSteer && targetDeg != null && abs(targetDeg - steeringDeg) > ON_TARGET_DEG) {
            state = SimState.WAITING_STEER
            note = "목표 조향각에 맞춘 뒤 재개하세요"
            return false
        }
        state = SimState.RUNNING
        targetAtResume = targetDeg
        note = "진행 중"
        return true
    }

    /** 처음 위치·기본 상태로. 속도 설정과 옵션은 그대로 둡니다. */
    fun reset() {
        state = SimState.IDLE
        pose = startPose
        travelledM = 0f
        targetAtResume = null
        note = ""
    }

    /** 멈춘 상태에서 지정 거리만 후진. 진행 중이거나 완료면 무시합니다. */
    fun nudge(distanceM: Float, steeringDeg: Float): Boolean {
        if (state == SimState.RUNNING || state == SimState.DONE) return false
        pose = advancePose(pose, -abs(distanceM), steeringDeg, geometry())
        travelledM += abs(distanceM)
        if (state == SimState.IDLE) {
            state = SimState.PAUSED
            note = "수동 이동"
        }
        return true
    }

    /**
     * 화면이 주기적으로 부릅니다.
     *
     * @param targetDeg 지금 위치에서 계산된 목표 조향각(안내가 꺼져 있으면 null)
     * @param done 목표 주차 위치 도달 여부
     * @param inReverse 기어가 R인지. 진행 중 R이 아니면 멈춥니다.
     * @param externalSpeedMps IDLE일 때만 쓰는 외부 속도 신호(시나리오·디버그 패널)
     * @param externalDirection IDLE일 때 외부 이동 방향(-1 후진, 1 전진, 0 정지)
     */
    fun tick(
        dtSec: Float,
        steeringDeg: Float,
        targetDeg: Float?,
        done: Boolean,
        inReverse: Boolean,
        externalSpeedMps: Float = 0f,
        externalDirection: Int = 0,
    ) {
        val dt = min(dtSec.coerceAtLeast(0f), MAX_TICK_SEC)

        when (state) {
            SimState.IDLE -> {
                if (externalSpeedMps > 0.01f && externalDirection != 0) {
                    pose = advancePose(pose, externalDirection * externalSpeedMps * dt, steeringDeg, geometry())
                }
            }

            SimState.RUNNING -> {
                if (done) {
                    state = SimState.DONE
                    note = "주차 완료 · 자동 정지"
                    return
                }
                if (!inReverse) {
                    pause("기어가 R이 아니라 멈춤")
                    return
                }
                if (waitForSteer && targetDeg != null) {
                    val prev = targetAtResume
                    val off = abs(targetDeg - steeringDeg) > ON_TARGET_DEG
                    val changed = prev != null && abs(targetDeg - prev) > TARGET_CHANGE_PAUSE_DEG
                    if (off || changed) {
                        state = SimState.WAITING_STEER
                        note = if (changed) "목표가 바뀌었어요 · 새 목표에 맞춘 뒤 재개하세요"
                        else "목표 조향각에 맞춘 뒤 재개하세요"
                        return
                    }
                }
                val ds = kmhToMps(speedKmh) * dt
                pose = advancePose(pose, -ds, steeringDeg, geometry())
                travelledM += ds
            }

            SimState.PAUSED, SimState.WAITING_STEER -> Unit

            SimState.DONE -> Unit
        }

        if (done && state != SimState.IDLE && state != SimState.DONE) {
            state = SimState.DONE
            note = "주차 완료 · 자동 정지"
        }
    }
}
