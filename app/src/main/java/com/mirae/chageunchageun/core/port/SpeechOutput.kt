package com.mirae.chageunchageun.core.port

import com.mirae.chageunchageun.core.model.SpeechPriority

/** 음성 출력(C1, B1, E2). 담당: E2 담당 */
interface SpeechOutput {
    /** 재생이 끝나면 반환합니다. 더 높은 우선순위가 오면 끊길 수 있습니다. */
    suspend fun speak(text: String, priority: SpeechPriority = SpeechPriority.NORMAL)

    fun stop()
}
