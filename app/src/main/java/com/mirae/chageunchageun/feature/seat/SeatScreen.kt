package com.mirae.chageunchageun.feature.seat

import androidx.compose.runtime.Composable
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.PlaceholderScreen

/** A1 시트 프로필 화면. 담당자가 이 파일부터 바꿉니다. */
@Composable
fun SeatScreen(graph: AppGraph) {
    PlaceholderScreen(
        title = "A1 시트 프로필",
        owner = "후",
        ports = "SeatController, DrivingSignals",
    )
}
