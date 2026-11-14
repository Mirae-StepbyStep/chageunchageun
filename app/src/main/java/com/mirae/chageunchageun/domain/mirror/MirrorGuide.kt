package com.mirae.chageunchageun.domain.mirror

/** A3 미러 조절 안내의 단계. 순서가 곧 안내 순서입니다. */
enum class MirrorStep(
    val title: String,
    val body: String,
    /** 실제 거울을 어떻게 움직이는지. 스위치 위치는 차종마다 달라 일반적인 설명만 둡니다. */
    val howTo: String = "",
    /** 언제 다 맞춘 것인지. 없으면 보이지 않습니다. */
    val doneWhen: String = "",
) {
    POSTURE("자세 먼저", "평소 운전 자세로 앉은 뒤에 미러를 맞춥니다."),
    ROOM(
        "룸미러",
        "운전할 때처럼 앉아서, 뒷유리 창이 거울에 꽉 차게 보이도록 맞춰요.",
        "거울 테두리를 잡고 위아래, 좌우로 살짝 기울여요. 유리면은 만지지 않아요.",
        "고개를 돌리지 않아도 뒷유리 양쪽 끝이 다 보이면 끝이에요.",
    ),
    LEFT(
        "왼쪽 사이드미러",
        "운전석 쪽 뒷문 손잡이가 거울 모서리에 살짝 걸치게 맞춰주세요.",
        "운전석 문 쪽 미러 스위치에서 L을 고르고 방향 버튼을 누릅니다.",
    ),
    RIGHT(
        "오른쪽 사이드미러",
        "조수석 쪽 뒷문 손잡이가 거울 모서리에 살짝 걸치게 맞춰주세요.",
        "운전석 문 쪽 미러 스위치에서 R을 고르고 방향 버튼을 누릅니다.",
    ),
    DONE("세팅 완료!", "미러 준비가 끝났습니다."),
}

/**
 * 미러 안내의 단계 이동과 그림 위치 계산. 순수 함수라 JVM 테스트로 확인합니다.
 */
object MirrorGuide {

    val steps: List<MirrorStep> = MirrorStep.entries

    fun next(step: MirrorStep): MirrorStep = steps[(step.ordinal + 1).coerceAtMost(steps.lastIndex)]

    fun previous(step: MirrorStep): MirrorStep = steps[(step.ordinal - 1).coerceAtLeast(0)]

    fun isFirst(step: MirrorStep): Boolean = step.ordinal == 0

    fun isLast(step: MirrorStep): Boolean = step.ordinal == steps.lastIndex

    /** 거울을 맞추는 그림이 있는 단계인지. 자세·완료 단계는 움직이지 않는 그림입니다. */
    fun isAnimated(step: MirrorStep): Boolean =
        step == MirrorStep.ROOM || step == MirrorStep.LEFT || step == MirrorStep.RIGHT

    /**
     * 애니메이션 한 바퀴의 진행률(0~1)을 "어긋난 정도"(1 = 어긋남, 0 = 맞음)로 바꿉니다.
     * 어긋난 상태로 잠깐 멈췄다가([HOLD_START]까지), 천천히 맞춰지고([MOVE_END]까지), 맞은 상태로 머뭅니다.
     */
    fun misalignment(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return when {
            p <= HOLD_START -> 1f
            p >= MOVE_END -> 0f
            else -> {
                val t = (p - HOLD_START) / (MOVE_END - HOLD_START)
                1f - t * t * (3f - 2f * t) // 부드럽게 출발하고 멈춤
            }
        }
    }

    /**
     * 한 바퀴의 처음과 끝에서 그림을 잠깐 흐리게 해, 맞은 상태에서 어긋난 상태로 뚝 끊겨 보이지 않게 합니다.
     * 돌려주는 값은 그림의 불투명도(0~1)입니다.
     */
    fun fade(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return when {
            p < FADE -> p / FADE
            p > 1f - FADE -> (1f - p) / FADE
            else -> 1f
        }
    }

    /** 한 바퀴의 길이(ms): 어긋남 약 1초, 맞추기 약 2초, 맞은 상태 약 3초. */
    const val LOOP_MS = 6_000

    const val HOLD_START = 0.17f
    const val MOVE_END = 0.5f
    const val FADE = 0.05f
}
