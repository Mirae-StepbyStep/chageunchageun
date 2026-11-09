package com.mirae.chageunchageun.feature.report

import androidx.compose.runtime.Composable
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.PlaceholderScreen

/** D1 주행 회고 화면. 담당자가 이 파일부터 바꿉니다. */
@Composable
fun ReportScreen(graph: AppGraph) {
    PlaceholderScreen(
        title = "D1 주행 회고",
        owner = "수현",
        ports = "DrivingSignals, DriveRuntime.events, data/",
    )
}
