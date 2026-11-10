package com.mirae.chageunchageun.platform.speech

import android.content.Context
import com.mirae.chageunchageun.core.model.SpeechResult
import com.mirae.chageunchageun.core.port.SpeechInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Pleos SpeechToText. 담당: E2 담당
 * 생성자는 AppGraph에 이미 연결돼 있으니 바꾸지 말고 본문만 채웁니다.
 * 구현 전에는 결과 없이 끝납니다.
 */
class PleosSpeechInput(private val context: Context) : SpeechInput {
    override fun listenOnce(): Flow<SpeechResult> = emptyFlow()
}
