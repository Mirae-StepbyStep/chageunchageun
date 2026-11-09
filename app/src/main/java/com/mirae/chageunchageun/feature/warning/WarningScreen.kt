package com.mirae.chageunchageun.feature.warning

import androidx.compose.runtime.Composable
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.PlaceholderScreen

/** C1 경고 안내 화면. 담당자가 이 파일부터 바꿉니다. */
@Composable
fun WarningScreen(graph: AppGraph) {
    PlaceholderScreen(
        title = "C1 경고 안내",
        owner = "재균",
        ports = "WarningSource, SpeechOutput, DriveRuntime.events",
    )
}
