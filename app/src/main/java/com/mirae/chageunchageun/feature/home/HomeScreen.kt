package com.mirae.chageunchageun.feature.home

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
import com.mirae.chageunchageun.core.model.DrivePhase
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.DrivingGate
import com.mirae.chageunchageun.ui.common.label
import com.mirae.chageunchageun.ui.theme.AppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(graph: AppGraph) {
    val vm: HomeViewModel = viewModel { HomeViewModel(graph.driveState, graph.controls, graph.driveRuntime) }
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
            HighBeamCard(ui)
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
private fun HighBeamCard(ui: HomeUi) {
    val text = when (ui.highBeam) {
        LampState.ON -> "켜짐"
        LampState.DAYTIME_RUNNING -> "주간주행등"
        LampState.OFF -> "꺼짐"
        null -> "-"
    }
    InfoCard("상향등", text, ui.highBeamSource.label(), highlight = ui.highBeam == LampState.ON)
}
