package com.mirae.chageunchageun.feature.warning

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/** C1 화면이 움직여도 되는지에 대한 규칙. 순수 함수라 JVM 테스트로 확인합니다. */
internal object WarningMotion {

    /** 주행 규제 중에는 화면을 스크롤하지 않습니다. */
    fun scrolls(canShowDetail: Boolean): Boolean = canShowDetail

    /** 정차 중이고 시스템 애니메이션 배율이 0이 아닐 때만 진입 애니메이션을 재생합니다. */
    fun animatesEntry(canShowDetail: Boolean, animatorScale: Float): Boolean = canShowDetail && animatorScale > 0f
}

/**
 * 정차 후 상세 카드의 진입 애니메이션. 시작부터 흐른 시간(초)으로 상태를 계산합니다. 한 번만 재생하고 반복하지 않습니다.
 * - 경고등: 1.2초 주기로 세 번 깜빡인 뒤 켜진 채로 둡니다(투명도만).
 * - 조치 단계: 0.25초 간격으로 0.5초씩 차례로 나타납니다.
 */
internal object EntryAnimation {
    private const val BLINK_CYCLE_S = 1.2f
    private const val BLINKS = 3
    private const val STEP_S = 0.5f
    private const val STEP_GAP_S = 0.25f

    fun totalSeconds(steps: Int): Float =
        max(BLINKS * BLINK_CYCLE_S, STEP_GAP_S * (steps - 1).coerceAtLeast(0) + STEP_S)

    fun telltaleAlpha(seconds: Float): Float {
        if (seconds >= BLINKS * BLINK_CYCLE_S) return 1f
        val phase = (seconds % BLINK_CYCLE_S) / BLINK_CYCLE_S
        return 0.2f + 0.8f * abs(cos(phase * PI)).toFloat()
    }

    /** 0이면 아직 안 보임, 1이면 제자리에 다 나타남. */
    fun stepProgress(seconds: Float, index: Int): Float = ((seconds - index * STEP_GAP_S) / STEP_S).coerceIn(0f, 1f)
}
