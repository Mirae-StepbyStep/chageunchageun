package com.example.chaguenchaguen.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chaguenchaguen.core.model.Source
import com.example.chaguenchaguen.runtime.DriveRuntime

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
        Card(Modifier.padding(16.dp)) {
            Text(
                "정차 후에 자세히 볼 수 있어요",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(24.dp),
            )
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
