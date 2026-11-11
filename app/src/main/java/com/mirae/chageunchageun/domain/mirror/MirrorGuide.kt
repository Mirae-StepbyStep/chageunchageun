package com.mirae.chageunchageun.domain.mirror

/** A3 미러 조절 안내의 단계. 순서가 곧 안내 순서입니다. */
enum class MirrorStep(val title: String, val body: String) {
    POSTURE("자세 먼저", "평소 운전 자세로 앉은 뒤에 미러를 맞춥니다."),
    ROOM("룸미러", "뒷유리 전체가 거울 가운데에 오게 맞춰주세요."),
    LEFT("왼쪽 사이드미러", "운전석 쪽 뒷문 손잡이가 거울 모서리에 살짝 걸치게 맞춰주세요."),
    RIGHT("오른쪽 사이드미러", "조수석 쪽 뒷문 손잡이가 거울 모서리에 보이게 맞춰주세요."),
    DONE("세팅 완료!", "이제 출발해도 좋습니다."),
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

    const val HOLD_START = 0.15f
    const val MOVE_END = 0.6f
}
