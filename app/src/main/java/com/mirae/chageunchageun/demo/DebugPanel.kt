package com.mirae.chageunchageun.demo

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
import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.core.model.WiperState
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.runtime.CONNECTED_PORTS
import com.mirae.chageunchageun.runtime.PortId
import com.mirae.chageunchageun.runtime.PortMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 개발·시연용 패널(debug 빌드에서만 메뉴에 보임).
 * 포트별로 실제·가짜를 고르고, 가짜 차량 값을 직접 바꾸고, 시나리오를 재생합니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(graph: AppGraph) {
    val bus = graph.fakeBus
    val scope = rememberCoroutineScope()
    val gear by bus.gear.collectAsStateWithLifecycle()
    val lamps by bus.lamps.collectAsStateWithLifecycle()
    val wiper by bus.frontWiper.collectAsStateWithLifecycle()
    val brake by bus.brakePressed.collectAsStateWithLifecycle()
    val ux by bus.uxRestricted.collectAsStateWithLifecycle()
    val steering by bus.steeringAngleDeg.collectAsStateWithLifecycle()
    val warnings by bus.warnings.collectAsStateWithLifecycle()
    val spoken by bus.spoken.collectAsStateWithLifecycle()
    val modes by graph.portModes.all.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf<Job?>(null) }
    var lastNote by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("디버그 패널", style = MaterialTheme.typography.headlineSmall)

        Text("포트 연결 (선택하면 실제 차량, 해제하면 가짜 차량)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PortId.entries.forEach { id ->
                val real = modes.getValue(id) == PortMode.REAL
                FilterChip(
                    selected = real,
                    onClick = { graph.portModes.set(id, if (real) PortMode.FAKE else PortMode.REAL) },
                    label = { Text("${id.label}: ${if (real) "실제" else "가짜"}") },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { graph.portModes.setAll(PortMode.REAL) }) { Text("모두 실제") }
            OutlinedButton(onClick = { graph.portModes.setAll(PortMode.FAKE) }) { Text("모두 가짜") }
            OutlinedButton(onClick = {
                PortId.entries.forEach { graph.portModes.set(it, if (it in CONNECTED_PORTS) PortMode.REAL else PortMode.FAKE) }
            }) { Text("처음 상태로") }
            // C1 시연(Virtual Console)에 필요한 세 포트만 실제로 바꿉니다. 앱을 다시 켜면 모두 가짜로 돌아갑니다.
            OutlinedButton(onClick = {
                listOf(PortId.WARNING, PortId.DRIVE_STATE, PortId.SPEECH_OUT).forEach { graph.portModes.set(it, PortMode.REAL) }
            }) { Text("C1 시연 준비") }
        }

        Text("가짜 차량 (아래 값은 모두 시연 신호, 가짜로 둔 포트에만 반영됩니다)", style = MaterialTheme.typography.titleMedium)

        Text("기어")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gear.entries.filter { it != Gear.UNKNOWN }.forEach {
                FilterChip(selected = gear == it, onClick = { bus.gear.value = it }, label = { Text(it.name) })
            }
        }
        FilterChip(selected = ux, onClick = { bus.uxRestricted.value = !ux }, label = { Text("주행 규제") })

        Text("등화·와이퍼·브레이크")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Lamp.entries.forEach { lamp ->
                val on = lamps.getValue(lamp) != LampState.OFF
                FilterChip(
                    selected = on,
                    onClick = { bus.setLamp(lamp, if (on) LampState.OFF else LampState.ON) },
                    label = { Text(lamp.name) },
                )
            }
            FilterChip(
                selected = wiper == WiperState.ON,
                onClick = { bus.frontWiper.value = if (wiper == WiperState.ON) WiperState.OFF else WiperState.ON },
                label = { Text("앞 와이퍼") },
            )
            FilterChip(selected = brake, onClick = { bus.brakePressed.value = !brake }, label = { Text("브레이크") })
        }

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
