package com.example.chaguenchaguen.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chaguenchaguen.core.model.Gear
import com.example.chaguenchaguen.core.model.TurnSignal
import com.example.chaguenchaguen.core.model.WarningType
import com.example.chaguenchaguen.runtime.AppGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 개발·시연용 패널(debug 빌드에서만 메뉴에 보임). 가짜 차량 값을 직접 바꾸고 시나리오를 재생합니다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(graph: AppGraph) {
    val bus = graph.fakeBus
    val scope = rememberCoroutineScope()
    val gear by bus.gear.collectAsStateWithLifecycle()
    val turn by bus.turnSignal.collectAsStateWithLifecycle()
    val ux by bus.uxRestricted.collectAsStateWithLifecycle()
    val speed by bus.speedMps.collectAsStateWithLifecycle()
    val steering by bus.steeringAngleDeg.collectAsStateWithLifecycle()
    val warnings by bus.warnings.collectAsStateWithLifecycle()
    val spoken by bus.spoken.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf<Job?>(null) }
    var lastNote by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("디버그 패널 (모든 값은 시연 신호)", style = MaterialTheme.typography.headlineSmall)

        Text("기어")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gear.entries.filter { it != Gear.UNKNOWN }.forEach {
                FilterChip(selected = gear == it, onClick = { bus.gear.value = it }, label = { Text(it.name) })
            }
        }
        Text("방향지시등")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TurnSignal.entries.forEach {
                FilterChip(selected = turn == it, onClick = { bus.turnSignal.value = it }, label = { Text(it.name) })
            }
        }
        FilterChip(selected = ux, onClick = { bus.uxRestricted.value = !ux }, label = { Text("주행 규제") })

        Text("속도 ${(speed * 3.6f).toInt()} km/h")
        Slider(value = speed, onValueChange = { bus.speedMps.value = it }, valueRange = 0f..30f)
        Text("조향각 ${steering.toInt()}°")
        Slider(value = steering, onValueChange = { bus.steeringAngleDeg.value = it }, valueRange = -540f..540f)

        Text("경고")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WarningType.entries.forEach { w ->
                FilterChip(
                    selected = w in warnings,
                    onClick = { bus.warnings.value = if (w in warnings) warnings - w else warnings + w },
                    label = { Text(w.name) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { bus.arrivals.tryEmit("시연 주차장") }) { Text("목적지 도착") }
            OutlinedButton(onClick = { bus.brakePressed.value = !bus.brakePressed.value }) { Text("브레이크 토글") }
        }

        Text("시나리오")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            graph.scenarioNames().forEach { name ->
                Button(onClick = {
                    playing?.cancel()
                    playing = scope.launch {
                        graph.scenarioPlayer.play(graph.loadScenario(name)) { step -> step.note?.let { lastNote = it } }
                    }
                }) { Text(name.removeSuffix(".json")) }
            }
            OutlinedButton(onClick = { playing?.cancel() }) { Text("정지") }
        }
        if (lastNote.isNotEmpty()) Text("최근 단계: $lastNote")

        Text("가짜 음성 출력(최근)")
        spoken.takeLast(5).forEach { Text("• $it") }
    }
}
