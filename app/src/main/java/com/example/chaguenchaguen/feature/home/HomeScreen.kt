package com.example.chaguenchaguen.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chaguenchaguen.core.model.DrivePhase
import com.example.chaguenchaguen.core.model.TurnSignal
import com.example.chaguenchaguen.runtime.AppGraph
import com.example.chaguenchaguen.ui.common.DrivingGate
import com.example.chaguenchaguen.ui.common.label
import com.example.chaguenchaguen.ui.theme.AppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(graph: AppGraph) {
    val vm: HomeViewModel = viewModel { HomeViewModel(graph.drivingSignals, graph.driveRuntime) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (ui.phase == DrivePhase.PARKED) "정차 중" else "주행 중",
            style = MaterialTheme.typography.displaySmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoCard("기어", ui.gear?.name ?: "-", ui.gearSource.label())
            InfoCard("속도", ui.speedKmh?.let { "$it km/h" } ?: "-", "")
            TurnSignalCard(ui)
        }
        // 상세 영역 예시: 주행 중에는 DrivingGate가 대신 짧은 안내를 보여 줍니다.
        DrivingGate(graph.driveRuntime) {
            Card(Modifier.padding(top = 8.dp)) {
                Text("정차 중에만 보이는 상세 안내 영역", Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, source: String, highlight: Boolean = false) {
    Card(
        colors = if (highlight) {
            CardDefaults.cardColors(containerColor = AppColors.ActiveBlue, contentColor = AppColors.Black)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineLarge)
            if (source.isNotEmpty()) Text(source, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TurnSignalCard(ui: HomeUi) {
    val text = when (ui.turnSignal) {
        TurnSignal.LEFT -> "◀ 왼쪽"
        TurnSignal.RIGHT -> "오른쪽 ▶"
        TurnSignal.HAZARD -> "비상등"
        TurnSignal.OFF -> "꺼짐"
        null -> "-"
    }
    val on = ui.turnSignal != null && ui.turnSignal != TurnSignal.OFF
    InfoCard("방향지시등", text, ui.turnSignalSource.label(), highlight = on)
}
