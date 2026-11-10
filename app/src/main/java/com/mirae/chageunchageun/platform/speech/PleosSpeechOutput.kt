package com.mirae.chageunchageun.platform.speech

import android.content.Context
import com.mirae.chageunchageun.core.model.SpeechPriority
import com.mirae.chageunchageun.core.port.SpeechOutput

/**
 * Pleos TextToSpeech. 담당: E2 담당
 * 생성자는 AppGraph에 이미 연결돼 있으니 바꾸지 말고 본문만 채웁니다.
 * 구현 전에는 아무 소리도 내지 않고 바로 반환합니다.
 */
class PleosSpeechOutput(private val context: Context) : SpeechOutput {
    override suspend fun speak(text: String, priority: SpeechPriority) = Unit
    override fun stop() = Unit
}
