package com.mirae.chageunchageun.feature.parking

import androidx.compose.runtime.Composable
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.PlaceholderScreen

/** B1 주차장 안내 화면. 담당자가 이 파일부터 바꿉니다. */
@Composable
fun ParkingScreen(graph: AppGraph) {
    PlaceholderScreen(
        title = "B1 주차장 안내",
        owner = "민서",
        ports = "LocationSource, VehicleSpecSource, SpeechOutput",
    )
}
