package com.mirae.chageunchageun.feature.voice

import androidx.compose.runtime.Composable
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.PlaceholderScreen

/** E2 음성 질문 화면. 담당자가 이 파일부터 바꿉니다. */
@Composable
fun VoiceScreen(graph: AppGraph) {
    PlaceholderScreen(
        title = "E2 음성 질문",
        owner = "미정",
        ports = "SpeechInput, SpeechOutput",
    )
}
