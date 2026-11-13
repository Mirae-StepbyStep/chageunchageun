package com.mirae.chageunchageun.domain.practice

import kotlin.math.abs

/** 운전자가 맞춰야 할 차량 상태. */
enum class PracticeTarget { ON, OFF, LEFT, RIGHT, CENTER }

/**
 * 연습의 한 단계: 운전자가 조작하면 차량 신호가 [target]에 닿는지 봅니다.
 * [skippable]은 차가 스스로 상태를 바꿀 수 있어 못 맞춰도 넘어갈 수 있는 단계입니다(예: 자동 전조등).
 */
data class PracticeStage(
    val target: PracticeTarget,
    val instruction: String,
    val confirmed: String,
    val skippable: Boolean = false,
)

/** A5 조작 연습 항목. 순서가 곧 목록 순서입니다. */
enum class PracticeItem(
    val title: String,
    /** 조작부가 어디 있는지. "자세히" 레벨에서만 보여 줍니다. */
    val where: String,
    /** 주의 문장. "자세히" 레벨에서만 보여 줍니다. */
    val caution: String,
    val stages: List<PracticeStage>,
) {
    LOW_BEAM(
        title = "전조등",
        where = "핸들 왼쪽 레버 끝에 있는 다이얼입니다.",
        caution = "AUTO 위치에서는 어두우면 저절로 켜져요. 꺼지지 않아도 정상입니다.",
        stages = listOf(
            PracticeStage(PracticeTarget.ON, "레버 끝 다이얼을 전조등 위치로 돌려주세요.", "전조등이 켜졌어요."),
            PracticeStage(PracticeTarget.OFF, "다이얼을 원래 위치로 되돌려주세요.", "전조등이 꺼졌어요.", skippable = true),
        ),
    ),
    HIGH_BEAM(
        title = "상향등",
        where = "핸들 왼쪽 레버를 앞뒤로 움직여 조작합니다. 전조등이 켜져 있어야 유지됩니다.",
        caution = "앞에 사람이나 차가 있으면 연습하지 않습니다.",
        stages = listOf(
            PracticeStage(PracticeTarget.ON, "왼쪽 레버를 계기판 쪽으로 밀어주세요.", "상향등이 켜졌어요."),
            PracticeStage(PracticeTarget.OFF, "레버를 몸 쪽으로 당겨 되돌려주세요.", "상향등이 꺼졌어요."),
        ),
    ),
    WIPER(
        title = "와이퍼",
        where = "핸들 오른쪽 레버입니다.",
        caution = "마른 유리에서는 짧게만 켭니다.",
        stages = listOf(
            PracticeStage(PracticeTarget.ON, "오른쪽 레버를 아래로 내려주세요.", "와이퍼가 움직여요."),
            PracticeStage(PracticeTarget.OFF, "레버를 OFF 위치로 올려주세요.", "와이퍼가 멈췄어요."),
        ),
    ),
    BRAKE(
        title = "브레이크",
        where = "두 페달 중 왼쪽에 있는 넓은 페달입니다. 오른발로 밟습니다.",
        caution = "기어가 P인지 먼저 확인합니다.",
        stages = listOf(
            PracticeStage(PracticeTarget.ON, "오른발로 브레이크 페달을 밟아주세요.", "브레이크를 밟았어요."),
            PracticeStage(PracticeTarget.OFF, "발을 천천히 떼주세요.", "브레이크에서 발을 뗐어요."),
        ),
    ),
    STEERING(
        title = "핸들",
        where = "양손으로 9시와 3시 위치를 잡습니다.",
        caution = "주변에 사람이 없는지 확인하고 돌립니다.",
        stages = listOf(
            PracticeStage(PracticeTarget.LEFT, "핸들을 왼쪽으로 돌려주세요.", "왼쪽으로 돌렸어요."),
            PracticeStage(PracticeTarget.RIGHT, "이번에는 오른쪽으로 돌려주세요.", "오른쪽으로 돌렸어요."),
            PracticeStage(PracticeTarget.CENTER, "핸들을 가운데로 되돌려주세요.", "가운데로 돌아왔어요."),
        ),
    ),
    ;

    /** 켜고 끄는 조작인지(핸들은 아님). */
    val isSwitch: Boolean get() = this != STEERING
}

/** 차량 신호를 연습 판단에 필요한 모양으로 줄인 값. */
sealed interface PracticeReading {
    data class Switch(val on: Boolean) : PracticeReading

    /** 조향각(도). 왼쪽이 음수입니다. */
    data class Angle(val deg: Float) : PracticeReading

    /** 값을 못 받음. 성공으로 치지 않습니다. */
    data object None : PracticeReading
}

/**
 * 진행 중인 연습 하나.
 * [primed]는 차량 값을 한 번이라도 받았는지입니다. 처음 받은 값으로 "먼저 꺼주세요" 단계를 붙일지 정합니다.
 */
data class PracticeSession(
    val item: PracticeItem,
    val stages: List<PracticeStage> = item.stages,
    val index: Int = 0,
    val primed: Boolean = false,
) {
    val isDone: Boolean get() = index >= stages.size
    val current: PracticeStage? get() = stages.getOrNull(index)

    /** 직전에 끝낸 단계. 확인 문장을 보여 줄 때 씁니다. */
    val lastConfirmed: PracticeStage? get() = stages.getOrNull(index - 1)
}

/** A5 연습의 단계 판단. 순수 함수라 JVM 테스트로 확인합니다. */
object PracticeGuide {

    val items: List<PracticeItem> = PracticeItem.entries

    /** 이 각도(도)를 넘게 돌리면 돌린 것으로 봅니다. 실제 차량 값 범위를 보기 전의 임시 기준입니다. */
    const val TURN_DEG = 15f

    /** 이 각도(도) 안이면 가운데로 봅니다. */
    const val CENTER_DEG = 5f

    private val turnOffFirst = PracticeStage(PracticeTarget.OFF, "이미 켜져 있어요. 먼저 꺼주세요.", "꺼졌어요. 이제 시작합니다.")

    fun start(item: PracticeItem): PracticeSession = PracticeSession(item)

    fun reached(target: PracticeTarget, reading: PracticeReading): Boolean = when (reading) {
        is PracticeReading.Switch -> when (target) {
            PracticeTarget.ON -> reading.on
            PracticeTarget.OFF -> !reading.on
            else -> false
        }
        is PracticeReading.Angle -> when (target) {
            PracticeTarget.LEFT -> reading.deg <= -TURN_DEG
            PracticeTarget.RIGHT -> reading.deg >= TURN_DEG
            PracticeTarget.CENTER -> abs(reading.deg) <= CENTER_DEG
            else -> false
        }
        PracticeReading.None -> false
    }

    /** 새 차량 값을 받았을 때의 다음 상태. 한 번에 한 단계만 넘어갑니다. */
    fun onReading(session: PracticeSession, reading: PracticeReading): PracticeSession {
        if (session.isDone || reading == PracticeReading.None) return session
        var s = session
        if (!s.primed) {
            val alreadyOn = s.index == 0 && s.item.isSwitch && reading == PracticeReading.Switch(on = true)
            s = s.copy(primed = true, stages = if (alreadyOn) listOf(turnOffFirst) + s.stages else s.stages)
            // 이미 켜진 상태에서 시작하면 그 값으로 첫 "켜기"를 통과시키지 않습니다.
            if (alreadyOn) return s
        }
        val stage = s.current ?: return s
        return if (reached(stage.target, reading)) s.copy(index = s.index + 1) else s
    }

    /** 운전자가 직접 확인했거나 건너뛴 경우. */
    fun confirmManually(session: PracticeSession): PracticeSession =
        if (session.isDone) session else session.copy(index = session.index + 1, primed = true)
}
