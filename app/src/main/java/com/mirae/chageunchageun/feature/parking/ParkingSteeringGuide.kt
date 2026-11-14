package com.mirae.chageunchageun.feature.parking

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 핸들 안내 문구. 화면·음성과 분리한 순수 Kotlin입니다(JVM 테스트 가능).
 *
 * 각도는 모두 "핸들(스티어링 휠) 각도"입니다. 양수 = 오른쪽, 음수 = 왼쪽.
 * 팀 디버그 패널과 시나리오가 DrivingSignals.steeringAngleDeg를 ±540°로 쓰고 있어 그 기준을 따릅니다.
 * Pleos registerSteeringWheelAngle의 실제 단위·범위는 아직 확인하지 않았습니다.
 *
 * 목표 각도는 ParkingPlanner가 차 위치와 주차칸으로 계산합니다. 이 파일은 그 목표를 말로 바꿉니다.
 */

/** 핸들을 끝까지 돌렸을 때의 각도(도). 가정값이며 차종별 실제 값으로 바꿔야 합니다. */
const val MAX_STEERING_WHEEL_DEG = 540f

/** 목표에서 이 각도 안이면 "도달". 1/8바퀴. */
const val ON_TARGET_DEG = 45f

/** 목표에서 이 각도 안이면 "곧 도달". */
const val NEAR_DEG = 135f

/** 주차칸이 차의 어느 쪽에 있는지. */
enum class ParkSide { LEFT, RIGHT }

/** 목표 대비 현재 상태. 색에 씁니다. */
enum class GaugeState { NO_TARGET, APPROACHING, NEAR, ON_TARGET, OVERSHOOT }

fun gaugeState(currentDeg: Float, targetDeg: Float?): GaugeState {
    if (targetDeg == null) return GaugeState.NO_TARGET
    val diff = abs(targetDeg - currentDeg)
    return when {
        diff <= ON_TARGET_DEG -> GaugeState.ON_TARGET
        diff <= NEAR_DEG -> GaugeState.NEAR
        abs(targetDeg) >= ON_TARGET_DEG && currentDeg * targetDeg > 0f && abs(currentDeg) > abs(targetDeg) ->
            GaugeState.OVERSHOOT
        else -> GaugeState.APPROACHING
    }
}

/** 핸들 각도를 -1..1로 바꿉니다. 게이지와 경로선에 씁니다. */
fun steeringFraction(angleDeg: Float, maxDeg: Float = MAX_STEERING_WHEEL_DEG): Float =
    (angleDeg / maxDeg).coerceIn(-1f, 1f)

/** 각도 크기를 "1/4바퀴", "반 바퀴", "1바퀴 반"처럼 1/4바퀴 단위로 읽습니다(화면용). */
fun turnAmountText(absDeg: Float): String = quarterText(absDeg, spoken = false)

/** 음성용. "1/4"를 TTS가 날짜로 읽지 않게 "4분의 1바퀴"처럼 씁니다. */
fun spokenTurnAmount(absDeg: Float): String = quarterText(absDeg, spoken = true)

private fun quarterText(deg: Float, spoken: Boolean): String {
    val quarters = (abs(deg) / 90f).roundToInt()
    if (quarters == 0) return "조금"
    val whole = quarters / 4
    val q = quarters % 4
    val quarter = if (spoken) "4분의 1" else "1/4"
    val threeQuarter = if (spoken) "4분의 3" else "3/4"
    return when {
        whole == 0 && q == 1 -> "${quarter}바퀴"
        whole == 0 && q == 2 -> "반 바퀴"
        whole == 0 && q == 3 -> "${threeQuarter}바퀴"
        q == 0 -> "${whole}바퀴"
        q == 2 -> "${whole}바퀴 반"
        q == 1 -> "${whole}과 ${quarter}바퀴"
        else -> "${whole}과 ${threeQuarter}바퀴"
    }
}

/** 핸들 위치. 예: "가운데", "오른쪽 3/4 바퀴", "왼쪽 끝까지". */
fun steeringPositionText(angleDeg: Float, maxDeg: Float = MAX_STEERING_WHEEL_DEG): String {
    val side = if (angleDeg < 0f) "왼쪽" else "오른쪽"
    return when {
        abs(angleDeg) < ON_TARGET_DEG -> "가운데"
        abs(angleDeg) >= maxDeg - ON_TARGET_DEG / 2 -> "$side 끝까지"
        else -> "$side ${turnAmountText(angleDeg)}"
    }
}

/** 부호가 붙은 각도. 예: "+90°", "-180°", "0°". */
fun signedDegText(deg: Float): String {
    val r = deg.roundToInt()
    return when {
        r > 0 -> "+$r°"
        r < 0 -> "-${-r}°"
        else -> "0°"
    }
}

/** 안내의 종류. 음성은 종류·방향·단계·목표가 바뀔 때만 말합니다. */
enum class InstructionKind {
    START_TURN, TURN_MORE, TURN_BACK, CENTER, HOLD, HOLD_CENTER,

    /** 목표 위치에 도착했지만 아직 P가 아님: 멈추고 P로 바꾸라는 안내. */
    ARRIVED_SHIFT_P,

    /** 목표 위치에서 P로 바꿈: 주차 완료. */
    PARKED;

    val isFinish: Boolean get() = this == ARRIVED_SHIFT_P || this == PARKED
}

/**
 * 지금 할 일 한 문장(화면)과 같은 내용의 음성 문장, 그리고 그 근거 숫자.
 * 화면과 음성이 항상 같은 목표를 설명하도록 한 객체에서 함께 만듭니다.
 *
 * @property remainingDeg 목표 - 현재. 양수면 오른쪽으로 더.
 * @property voiceKey 이 값이 바뀔 때만 음성으로 읽습니다(남은 양이 조금씩 줄 때는 그대로).
 */
data class SteeringInstruction(
    val kind: InstructionKind,
    val headline: String,
    val voiceText: String,
    val currentDeg: Float,
    val targetDeg: Float,
    val remainingDeg: Float,
    val voiceKey: String,
) {
    val onTarget: Boolean get() = abs(remainingDeg) <= ON_TARGET_DEG
}

fun steeringInstruction(
    currentDeg: Float,
    targetDeg: Float,
    done: Boolean = false,
    phase: ParkPhase? = null,
    inPark: Boolean = false,
): SteeringInstruction {
    val remaining = targetDeg - currentDeg
    val dir = if (remaining > 0f) "오른쪽" else "왼쪽"
    val amount = turnAmountText(remaining)
    val said = spokenTurnAmount(remaining)
    val onTarget = abs(remaining) <= ON_TARGET_DEG
    val targetIsCenter = abs(targetDeg) < ON_TARGET_DEG
    val currentIsCenter = abs(currentDeg) < ON_TARGET_DEG

    data class Line(val kind: InstructionKind, val screen: String, val voice: String)

    val line = when {
        done && inPark -> Line(
            InstructionKind.PARKED,
            "주차를 완료했습니다. 주변을 확인한 뒤 내리세요.",
            "주차를 완료했습니다. 주변을 확인한 뒤 내려 주세요.",
        )
        done -> Line(
            InstructionKind.ARRIVED_SHIFT_P,
            "주차 위치에 도착했습니다. 브레이크를 밟고 기어를 P로 바꾸세요.",
            "목표 주차 위치에 도착했습니다. 브레이크를 밟고 기어를 P로 바꿔 주세요.",
        )
        onTarget && targetIsCenter -> Line(
            InstructionKind.HOLD_CENTER,
            "핸들을 중앙에 두고 천천히 후진하세요.",
            "핸들을 중앙에 두고 천천히 후진하세요.",
        )
        onTarget -> Line(
            InstructionKind.HOLD,
            "목표 조향각에 도달했습니다. 핸들을 유지하세요.",
            "목표 조향각에 도달했습니다. 핸들을 유지하세요.",
        )
        targetIsCenter -> Line(
            InstructionKind.CENTER,
            "핸들을 중앙으로 정렬하세요.",
            "이제 핸들을 중앙으로 정렬하세요. ${dir}으로 $said 돌리세요.",
        )
        currentIsCenter -> Line(
            InstructionKind.START_TURN,
            "핸들을 ${dir}으로 $amount 돌리세요.",
            "핸들을 ${dir}으로 $said 돌리세요.",
        )
        currentDeg * targetDeg > 0f && abs(targetDeg) > abs(currentDeg) -> Line(
            InstructionKind.TURN_MORE,
            "${dir}으로 $amount 더 돌리세요.",
            "${dir}으로 $said 더 돌리세요.",
        )
        else -> Line(
            InstructionKind.TURN_BACK,
            "이제 핸들을 ${dir}으로 $amount 돌리세요.",
            "이제 핸들을 ${dir}으로 $said 돌리세요.",
        )
    }

    val keyDir = when (line.kind) {
        InstructionKind.HOLD, InstructionKind.HOLD_CENTER,
        InstructionKind.ARRIVED_SHIFT_P, InstructionKind.PARKED -> ""
        else -> dir
    }
    // 목표를 1/4바퀴 단위로 묶어 키에 넣습니다. 목표가 크게 바뀌면 다시 말하고, 10° 흔들림에는 말하지 않습니다.
    val targetQuarter = (targetDeg / 90f).roundToInt()

    return SteeringInstruction(
        kind = line.kind,
        headline = line.screen,
        voiceText = line.voice,
        currentDeg = currentDeg,
        targetDeg = targetDeg,
        remainingDeg = remaining,
        voiceKey = "${voiceGroup(line.kind)}:$keyDir:${phase?.name ?: ""}:$targetQuarter",
    )
}

/** "돌리세요"와 "더 돌리세요"는 같은 동작이라 같은 음성 키로 묶습니다(돌리는 중에 다시 말하지 않게). */
private fun voiceGroup(kind: InstructionKind): String = when (kind) {
    InstructionKind.START_TURN, InstructionKind.TURN_MORE -> "TURN"
    else -> kind.name
}

/** 화면에 쓰는 "오른쪽 1/4바퀴 (+90°)" 형식. */
fun steeringWithDegText(deg: Float): String = "${steeringPositionText(deg)} (${signedDegText(deg)})"

/** 남은 조작량. 예: "오른쪽 반 바퀴 (+180°)", 목표 안이면 "도달". */
fun remainingText(remainingDeg: Float): String {
    if (abs(remainingDeg) <= ON_TARGET_DEG) return "도달 (${signedDegText(remainingDeg)})"
    val dir = if (remainingDeg > 0f) "오른쪽" else "왼쪽"
    return "$dir ${turnAmountText(remainingDeg)} (${signedDegText(remainingDeg)})"
}

const val VOICE_START_TEXT = "주차 안내를 시작합니다. 주변을 확인해 주세요."

/**
 * 주차 코치 음성 규칙. 화면과 같은 [SteeringInstruction]만 읽습니다.
 *
 * - 안내가 켜지면 시작 안내 → (시작 안내가 끝날 즈음) 지금 할 일.
 * - [SteeringInstruction.voiceKey]가 바뀌고 [settleMs] 동안 유지되면 새 안내.
 * - 직전 음성에서 [gapMs]가 지나야 다음 안내. 도착(P로 바꾸기)·주차 완료 안내는 바로.
 * - 같은 키는 다시 말하지 않습니다. 안내가 꺼지면 처음 상태로 돌아갑니다.
 *
 * 순수 Kotlin이라 시간(nowMs)을 밖에서 넣고 JVM 테스트로 확인합니다.
 */
class VoiceGuidePolicy(
    private val settleMs: Long = 700L,
    private val gapMs: Long = 2500L,
    private val afterStartMs: Long = 3500L,
) {
    private var startedAt: Long? = null
    private var lastKey: String? = null
    private var lastSpokenAt: Long = Long.MIN_VALUE / 2
    private var candidateKey: String? = null
    private var candidateSince: Long = 0L

    /** 지금 말해야 할 문장. 없으면 null. 200ms 정도마다 부릅니다. */
    fun next(active: Boolean, instruction: SteeringInstruction?, nowMs: Long): String? {
        if (!active || instruction == null) {
            reset()
            return null
        }

        val started = startedAt
        if (started == null) {
            startedAt = nowMs
            lastSpokenAt = nowMs
            return VOICE_START_TEXT
        }

        val key = instruction.voiceKey
        if (key == lastKey) {
            candidateKey = null
            return null
        }
        if (key != candidateKey) {
            candidateKey = key
            candidateSince = nowMs
        }

        val done = instruction.kind.isFinish
        if (!done) {
            if (nowMs - started < afterStartMs) return null
            if (nowMs - candidateSince < settleMs) return null
            if (nowMs - lastSpokenAt < gapMs) return null
        }

        lastKey = key
        lastSpokenAt = nowMs
        candidateKey = null
        return instruction.voiceText
    }

    fun reset() {
        startedAt = null
        lastKey = null
        lastSpokenAt = Long.MIN_VALUE / 2
        candidateKey = null
    }
}
