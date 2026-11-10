package com.mirae.chageunchageun.core.port

import com.mirae.chageunchageun.core.model.SpeechResult
import kotlinx.coroutines.flow.Flow

/** 음성 입력(E2). 담당: E2 담당 */
interface SpeechInput {
    /** 한 번 듣기. 최종 결과를 내보내고 끝납니다. */
    fun listenOnce(): Flow<SpeechResult>
}
