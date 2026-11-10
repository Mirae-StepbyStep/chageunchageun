package com.mirae.chageunchageun.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirae.chageunchageun.core.model.CarSize
import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.NOT_YET
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.runtime.PortId
import com.mirae.chageunchageun.runtime.PortMode
import com.mirae.chageunchageun.ui.common.label
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 신호 점검(debug 빌드에서만 메뉴에 보임). 포트가 지금 내보내는 값을 한 줄씩 보여 줍니다.
 * 값을 못 받으면 사유(Unavailable.reason)가 그대로 보이므로, 어댑터를 붙이며 여기서 확인합니다.
 * 실제·가짜 전환은 디버그 메뉴의 "포트 연결"에서 합니다.
 */
@Composable
fun SignalCheckScreen(graph: AppGraph) {
    val modes by graph.portModes.all.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("신호 점검", style = MaterialTheme.typography.headlineSmall)
        Text("포트가 지금 내보내는 값입니다. 실제·가짜 전환은 디버그 메뉴에서 합니다.")

        PortHeader(PortId.DRIVE_STATE, modes)
        SignalRow("기어", graph.driveState.gear) { it.name }
        SignalRow("주행 규제", graph.driveState.uxRestricted) { if (it) "규제 중" else "없음" }

        PortHeader(PortId.CONTROLS, modes)
        Lamp.entries.forEach { lamp ->
            SignalRow("등화 ${lamp.name}", remember(lamp) { graph.controls.lamp(lamp) }) { it.name }
        }
        SignalRow("앞 와이퍼", graph.controls.frontWiper) { it.name }
        SignalRow("브레이크 페달", graph.controls.brakePressed) { if (it) "밟음" else "뗌" }

        PortHeader(PortId.STEERING, modes)
        SignalRow("조향각", graph.steering.angleDeg) { "${"%.1f".format(it)}°" }

        PortHeader(PortId.WARNING, modes)
        SignalRow("켜진 경고", graph.warningSource.activeWarnings) { set ->
            if (set.isEmpty()) "없음" else set.joinToString { it.name }
        }

        PortHeader(PortId.VEHICLE_SPEC, modes)
        // 한 번 조회하는 값이라 모드가 바뀔 때마다 다시 조회합니다.
        val size by produceState<Signal<CarSize>>(NOT_YET, modes[PortId.VEHICLE_SPEC]) {
            value = NOT_YET
            value = graph.vehicleSpec.exteriorSize()
        }
        SignalLine("외부 크기", size) { "${it.lengthMm} × ${it.widthMm} × ${it.heightMm} mm" }

        PortHeader(PortId.LOCATION, modes)
        SignalRow("위치", graph.locationSource.location) { "%.5f, %.5f".format(it.latitude, it.longitude) }
        val lastArrival by remember { graph.locationSource.arrivals.map { "${it.ifEmpty { "(이름 없음)" }} ${now()}" } }
            .collectAsStateWithLifecycle(initialValue = "이 화면을 연 뒤 도착 이벤트 없음")
        PlainLine("도착 이벤트", lastArrival)

        PortHeader(PortId.SPEECH_OUT, modes)
        PlainLine("말하기", "내보내는 값이 없는 포트입니다. 가짜 모드의 출력은 디버그 메뉴에 보입니다.")
        PortHeader(PortId.SPEECH_IN, modes)
        PlainLine("듣기", "한 번 듣기 요청형 포트라 여기서는 보지 않습니다.")
    }
}

@Composable
private fun PortHeader(id: PortId, modes: Map<PortId, PortMode>) {
    val real = modes.getValue(id) == PortMode.REAL
    HorizontalDivider(Modifier.padding(top = 10.dp))
    Text(
        "${id.label} · ${if (real) "실제" else "가짜"}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun <T> SignalRow(name: String, flow: Flow<Signal<T>>, format: (T) -> String) {
    val signal by flow.collectAsStateWithLifecycle(initialValue = NOT_YET)
    SignalLine(name, signal, format)
}

@Composable
private fun <T> SignalLine(name: String, signal: Signal<T>, format: (T) -> String) {
    when (signal) {
        is Signal.Value -> PlainLine(name, format(signal.value), "${signal.source.label()} · ${time(signal.atMillis)}")
        is Signal.Unavailable -> PlainLine(name, "값 없음", "사유: ${signal.reason}")
    }
}

@Composable
private fun PlainLine(name: String, value: String, detail: String = "") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(name, Modifier.weight(1f))
        Text(value, Modifier.weight(1.4f), fontWeight = FontWeight.Medium)
        Text(detail, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
    }
}

private fun time(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(millis))

private fun now(): String = time(System.currentTimeMillis())
