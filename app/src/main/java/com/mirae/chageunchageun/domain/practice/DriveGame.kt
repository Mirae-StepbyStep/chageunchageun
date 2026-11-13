package com.mirae.chageunchageun.domain.practice

import kotlin.math.abs

/** 도로 주행 게임에서 만나는 상황. */
enum class GameEventType(val title: String) {
    OBSTACLE("공사 구간"),
    STOP_SIGNAL("정지 신호"),
    TUNNEL("터널"),
    RAIN("비"),
}

/**
 * 도로 위의 상황 하나. 거리는 출발점부터의 미터입니다.
 * - OBSTACLE: [atM] 지점, 도로 가로 위치 [laneX](-1 왼쪽 끝 ~ 1 오른쪽 끝)에 장애물이 있습니다.
 * - STOP_SIGNAL: [atM]이 정지선입니다.
 * - TUNNEL, RAIN: [atM]부터 [endM]까지 이어집니다.
 */
data class GameEvent(
    val type: GameEventType,
    val atM: Float,
    val endM: Float = atM,
    val laneX: Float = 0f,
)

/** 상황 하나의 결과. 신호를 못 받아 판단하지 못한 것은 [UNCHECKED]로 남깁니다(성공으로 치지 않음). */
enum class GameOutcome { GOOD, MISSED, UNCHECKED }

/**
 * 한 프레임의 입력. 차량 신호에서 오며, 못 받은 값은 null입니다(0이나 false로 바꾸지 않음).
 * [steerDeg]는 왼쪽이 음수입니다.
 */
data class GameInput(
    val steerDeg: Float? = null,
    val brake: Boolean? = null,
    val lowBeam: Boolean? = null,
    val wiper: Boolean? = null,
)

data class GameState(
    val distanceM: Float = 0f,
    val speedMps: Float = DriveGame.CRUISE_MPS,
    /** 내 차의 가로 위치. -1 왼쪽 끝 ~ 1 오른쪽 끝. */
    val carX: Float = 0f,
    /** 정지 신호 앞에서 멈춰 있던 시간(초). */
    val stoppedSec: Float = 0f,
    /** 상황(트랙의 순번)별 결과. 아직 지나지 않은 상황은 없습니다. */
    val outcomes: Map<Int, GameOutcome> = emptyMap(),
    /** 결과가 정해진 지점(m). 결과 문구를 얼마 동안 보여 줄지, 비가 언제 그칠지에 씁니다. */
    val decidedAtM: Map<Int, Float> = emptyMap(),
    /** 주행 중에 전조등·와이퍼가 꺼져 있는 것을 본 적이 있는지. 켠 채로 시작하면 조작 없이 성공하지 않게 합니다. */
    val lowBeamSeenOff: Boolean = false,
    val wiperSeenOff: Boolean = false,
) {
    val finished: Boolean get() = distanceM >= DriveGame.FINISH_M
}

/**
 * A5 도로 주행 게임의 규칙. 차는 일정 속도로 스스로 달리고, 운전자는 실제 조작부로 상황에 대응합니다.
 * 가속 페달은 읽을 수 없어 쓰지 않습니다. 순수 함수라 JVM 테스트로 확인합니다.
 */
object DriveGame {

    const val CRUISE_MPS = 14f
    const val FINISH_M = 960f

    private const val BRAKE_DECEL = 9f
    private const val ACCEL = 5f

    /** 핸들을 이 각도(도)만큼 돌리면 가로 이동이 가장 빨라집니다. */
    const val FULL_STEER_DEG = 60f
    private const val LATERAL_PER_SEC = 0.9f

    /** 장애물과 이만큼(가로 위치) 떨어져 지나가야 피한 것으로 봅니다. */
    const val CLEARANCE = 0.5f

    /** 정지선 앞 이 거리(m) 안에서 멈춰야 합니다. */
    const val STOP_ZONE_M = 60f

    /** 멈춘 뒤 이 시간(초)이 지나면 출발 신호로 바뀝니다. */
    const val STOP_HOLD_SEC = 1.5f

    /** 터널·비 구간에 들어선 뒤 이 거리(m) 안에 켜야 합니다. 레버를 찾을 시간으로 8초를 줍니다. */
    const val REACT_M = 112f

    /** 상황이 화면에 보이기 시작하는 거리(m). 안내 문구도 이때부터 나옵니다. */
    const val NOTICE_M = 110f

    /** 결과 문구를 보여 주는 거리(m). 약 3초입니다. */
    const val FEEDBACK_M = 42f

    /** 와이퍼를 켠 뒤 이 거리(m)를 더 가면 비가 그칩니다. 마른 유리에서 와이퍼가 오래 돌지 않게 합니다. */
    const val RAIN_CLEAR_M = 35f

    val track: List<GameEvent> = listOf(
        GameEvent(GameEventType.OBSTACLE, atM = 170f, laneX = 0f),
        GameEvent(GameEventType.STOP_SIGNAL, atM = 330f),
        GameEvent(GameEventType.TUNNEL, atM = 450f, endM = 610f),
        GameEvent(GameEventType.RAIN, atM = 740f, endM = 900f),
    )

    fun start(): GameState = GameState()

    /** 정지 신호가 아직 "정지"인지. 멈춰서 기다렸거나 이미 지나갔으면 "출발"입니다. */
    fun isStopSignalOn(state: GameState, index: Int): Boolean =
        index !in state.outcomes && state.stoppedSec < STOP_HOLD_SEC

    /** 비가 오는 중인지. 와이퍼를 켜면 조금 뒤에 그칩니다. */
    fun isRaining(state: GameState): Boolean = track.withIndex().any { (i, event) ->
        event.type == GameEventType.RAIN &&
            state.distanceM in event.atM..event.endM &&
            !rainCleared(state, i)
    }

    private fun rainCleared(state: GameState, index: Int): Boolean {
        val goodAt = state.decidedAtM[index] ?: return false
        return state.outcomes[index] == GameOutcome.GOOD && state.distanceM > goodAt + RAIN_CLEAR_M
    }

    /** [dtSec]초가 지난 뒤의 상태. */
    fun step(state: GameState, input: GameInput, dtSec: Float): GameState {
        if (state.finished || dtSec <= 0f) return state
        val dt = dtSec.coerceAtMost(0.1f)
        var outcomes = state.outcomes
        var decidedAt = state.decidedAtM

        // 브레이크 신호를 못 받는 차에서는 정지 신호 앞에서 차가 스스로 멈춥니다(결과는 "확인 불가").
        val signalIndex = track.indices.firstOrNull { track[it].type == GameEventType.STOP_SIGNAL && it !in outcomes }
        val signal = signalIndex?.let { track[it] }
        val toLine = signal?.let { it.atM - state.distanceM }
        val waiting = signalIndex != null && isStopSignalOn(state, signalIndex)
        val autoStop = input.brake == null && waiting && toLine != null && toLine < 14f
        val braking = input.brake == true || autoStop

        val speed = if (braking) {
            (state.speedMps - BRAKE_DECEL * dt).coerceAtLeast(0f)
        } else {
            (state.speedMps + ACCEL * dt).coerceAtMost(CRUISE_MPS)
        }
        val distance = state.distanceM + speed * dt

        // 핸들: 달리는 동안에만 가로로 움직입니다.
        val steer = ((input.steerDeg ?: 0f) / FULL_STEER_DEG).coerceIn(-1f, 1f)
        val carX = (state.carX + steer * LATERAL_PER_SEC * dt * (speed / CRUISE_MPS)).coerceIn(-1f, 1f)

        // 정지선 앞에서 멈춰 있던 시간
        val inStopZone = toLine != null && toLine in 0f..STOP_ZONE_M
        val stoppedSec = if (waiting && inStopZone && speed < 0.3f) state.stoppedSec + dt else state.stoppedSec

        val lowBeamSeenOff = state.lowBeamSeenOff || input.lowBeam == false
        val wiperSeenOff = state.wiperSeenOff || input.wiper == false

        track.forEachIndexed { i, event ->
            if (i in outcomes) return@forEachIndexed
            val outcome: GameOutcome? = when (event.type) {
                GameEventType.OBSTACLE -> when {
                    // 조향각을 못 받는 차에서는 장애물을 내보내지 않습니다.
                    input.steerDeg == null && distance >= event.atM - NOTICE_M -> GameOutcome.UNCHECKED
                    state.distanceM < event.atM && distance >= event.atM ->
                        if (abs(carX - event.laneX) >= CLEARANCE) GameOutcome.GOOD else GameOutcome.MISSED
                    else -> null
                }
                GameEventType.STOP_SIGNAL -> when {
                    stoppedSec >= STOP_HOLD_SEC -> if (input.brake == null) GameOutcome.UNCHECKED else GameOutcome.GOOD
                    distance >= event.atM -> GameOutcome.MISSED
                    else -> null
                }
                GameEventType.TUNNEL -> switchOutcome(input.lowBeam, lowBeamSeenOff, distance, event)
                GameEventType.RAIN -> switchOutcome(input.wiper, wiperSeenOff, distance, event)
            }
            if (outcome != null) {
                outcomes = outcomes + (i to outcome)
                decidedAt = decidedAt + (i to distance)
            }
        }

        val passedSignal = signalIndex != null && signalIndex in outcomes
        return state.copy(
            distanceM = distance,
            speedMps = speed,
            carX = carX,
            stoppedSec = if (passedSignal) 0f else stoppedSec,
            outcomes = outcomes,
            decidedAtM = decidedAt,
            lowBeamSeenOff = lowBeamSeenOff,
            wiperSeenOff = wiperSeenOff,
        )
    }

    /**
     * 구간 안에서 직접 켜면 성공, 반응 거리를 넘기면 놓친 것으로 봅니다.
     * 처음부터 켜져 있던 것([seenOff]가 거짓)은 직접 켠 것이 아니라서 성공으로 치지 않습니다.
     */
    private fun switchOutcome(on: Boolean?, seenOff: Boolean, distance: Float, event: GameEvent): GameOutcome? = when {
        distance < event.atM -> null
        on == true && seenOff -> GameOutcome.GOOD
        distance < event.atM + REACT_M -> null
        on == false -> GameOutcome.MISSED
        else -> GameOutcome.UNCHECKED
    }

    /** 지금 다가오거나 진행 중인 상황(트랙 순번). 장애물을 내보내지 않은 경우는 건너뜁니다. */
    fun currentEvent(state: GameState): Int? = track.indices.firstOrNull { i ->
        val event = track[i]
        val hidden = event.type == GameEventType.OBSTACLE && state.outcomes[i] == GameOutcome.UNCHECKED
        val until = when (event.type) {
            GameEventType.OBSTACLE, GameEventType.STOP_SIGNAL -> (state.decidedAtM[i] ?: event.atM) + FEEDBACK_M
            else -> event.endM + FEEDBACK_M
        }
        !hidden && state.distanceM >= event.atM - NOTICE_M && state.distanceM < until
    }

    /** 화면 위쪽에 보여 줄 한 줄 안내. */
    fun prompt(state: GameState, input: GameInput): String {
        if (state.finished) return "주행 완료!"
        val index = currentEvent(state) ?: return "그대로 갑니다."
        val event = track[index]
        val outcome = state.outcomes[index]
        return when (event.type) {
            GameEventType.OBSTACLE -> when (outcome) {
                GameOutcome.GOOD -> "잘 피했습니다."
                GameOutcome.MISSED -> "공사 구간은 미리 핸들을 돌려 피합니다."
                else -> "앞에 공사 구간! 핸들을 조금 돌려 피해주세요."
            }
            GameEventType.STOP_SIGNAL -> when (outcome) {
                GameOutcome.GOOD -> "잘 멈췄습니다. 출발 신호입니다. 발을 떼주세요."
                GameOutcome.MISSED -> "정지 신호에서는 정지선 앞에 멈춥니다."
                GameOutcome.UNCHECKED -> "이 차에서는 브레이크를 확인할 수 없습니다."
                null -> when {
                    state.speedMps < 0.3f && event.atM - state.distanceM > STOP_ZONE_M -> "정지선 가까이 가서 멈춰주세요."
                    state.speedMps < 0.3f -> "좋습니다. 밟은 채로 기다려주세요."
                    event.atM - state.distanceM > STOP_ZONE_M -> "곧 정지 신호입니다. 브레이크를 준비해주세요."
                    else -> "지금 브레이크! 정지선 앞에 멈춰주세요."
                }
            }
            GameEventType.TUNNEL -> when {
                state.distanceM < event.atM -> "곧 터널입니다. 전조등을 준비해주세요."
                state.distanceM >= event.endM -> if (input.lowBeam == true) "터널을 나왔습니다. 전조등을 원래대로 되돌려주세요." else "터널을 지났습니다."
                outcome == GameOutcome.GOOD -> "전조등 ON. 잘 보입니다."
                outcome == GameOutcome.UNCHECKED -> "이 차에서는 전조등을 확인할 수 없습니다."
                outcome == GameOutcome.MISSED -> "터널에서는 전조등을 켭니다."
                input.lowBeam == true -> "이미 켜져 있습니다. 껐다가 다시 켜주세요."
                else -> "터널입니다. 전조등을 켜주세요."
            }
            GameEventType.RAIN -> when {
                state.distanceM < event.atM -> "곧 비가 옵니다. 와이퍼를 준비해주세요."
                outcome == GameOutcome.GOOD && !isRaining(state) ->
                    if (input.wiper == true) "비가 그쳤습니다. 와이퍼를 꺼주세요." else "와이퍼 OFF. 잘했습니다."
                state.distanceM >= event.endM -> if (input.wiper == true) "비가 그쳤습니다. 와이퍼를 꺼주세요." else "비가 그쳤습니다."
                outcome == GameOutcome.GOOD -> "와이퍼 ON. 잘 보입니다."
                outcome == GameOutcome.UNCHECKED -> "이 차에서는 와이퍼를 확인할 수 없습니다."
                outcome == GameOutcome.MISSED -> "비가 오면 와이퍼를 켭니다."
                input.wiper == true -> "이미 켜져 있습니다. 껐다가 다시 켜주세요."
                else -> "비가 옵니다. 와이퍼를 켜주세요."
            }
        }
    }
}
