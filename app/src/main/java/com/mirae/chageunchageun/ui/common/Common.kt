package com.mirae.chageunchageun.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.runtime.DriveRuntime

/**
 * 상세 화면을 감쌉니다. 주행 중이거나 주행 규제 중이면 [content] 대신 짧은 안내를 보여 줍니다.
 * 각 feature가 주행 제한을 따로 판단하지 않도록 이것만 씁니다.
 */
@Composable
fun DrivingGate(runtime: DriveRuntime, content: @Composable () -> Unit) {
    val canShow by runtime.canShowDetail.collectAsStateWithLifecycle()
    if (canShow) {
        content()
    } else {
        // 화면이 열리는 실제 조건(기어 P)을 그대로 알려 줍니다.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card {
                Text(
                    "기어를 P에 두면 볼 수 있습니다",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
                )
            }
        }
    }
}

/** 값의 출처 표시. 시연 신호(SCRIPTED)를 실제 값처럼 보이지 않게 합니다. */
fun Source?.label(): String = when (this) {
    Source.PLEOS -> "Pleos SDK"
    Source.AAOS -> "AAOS"
    Source.NAVI -> "NaviHelper"
    Source.SCRIPTED -> "시연 신호"
    null -> "값 없음"
}

/** 아직 구현 전인 기능 화면. 담당자가 이 파일을 자기 화면으로 바꿉니다. */
@Composable
fun PlaceholderScreen(title: String, owner: String, ports: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text("담당: $owner")
        Text("사용할 포트: $ports")
        Text("feature/home 의 예시(HomeViewModel → HomeScreen)를 따라 만드세요.")
    }
}
