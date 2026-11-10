package com.mirae.chageunchageun.feature.practice

import androidx.compose.runtime.Composable
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.PlaceholderScreen

/** A5 정차 중 조작 연습 화면(시간이 되면 구현). 담당자가 이 파일부터 바꿉니다. */
@Composable
fun PracticeScreen(graph: AppGraph) {
    PlaceholderScreen(
        title = "A5 조작 연습",
        owner = "후",
        ports = "ControlsSource, SteeringSource",
    )
}
