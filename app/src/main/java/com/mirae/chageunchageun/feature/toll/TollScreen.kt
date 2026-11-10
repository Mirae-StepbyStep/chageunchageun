package com.mirae.chageunchageun.feature.toll

import androidx.compose.runtime.Composable
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.PlaceholderScreen

/** B2 톨게이트 안내 화면(시간이 되면 구현). 담당자가 이 파일부터 바꿉니다. */
@Composable
fun TollScreen(graph: AppGraph) {
    PlaceholderScreen(
        title = "B2 톨게이트 안내",
        owner = "후(주행 전 미리 보기), 민서(주행 중 안내)",
        ports = "LocationSource, SpeechOutput",
    )
}
