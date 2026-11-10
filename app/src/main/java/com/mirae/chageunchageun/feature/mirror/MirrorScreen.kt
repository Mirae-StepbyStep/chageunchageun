package com.mirae.chageunchageun.feature.mirror

import androidx.compose.runtime.Composable
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.PlaceholderScreen

/** A3 미러 조절 안내 화면(필수). 출발하면 숨깁니다. 담당자가 이 파일부터 바꿉니다. */
@Composable
fun MirrorScreen(graph: AppGraph) {
    PlaceholderScreen(
        title = "A3 미러 조절 안내",
        owner = "후",
        ports = "없음 (화면 안내). 숨김 판단은 DriveRuntime.canShowDetail",
    )
}
