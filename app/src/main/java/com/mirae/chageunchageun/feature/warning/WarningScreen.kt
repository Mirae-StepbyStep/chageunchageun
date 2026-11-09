package com.mirae.chageunchageun.feature.warning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mirae.chageunchageun.core.model.Severity
import com.mirae.chageunchageun.domain.guidance.WarningGuidance
import com.mirae.chageunchageun.domain.guidance.WarningGuide
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.DrivingGate
import com.mirae.chageunchageun.ui.common.label
import com.mirae.chageunchageun.ui.theme.AppColors

/** C1 경고 안내 화면. 주행 중에는 심각도와 제목만, 정차 후에는 조치까지 보여 줍니다. */
@Composable
fun WarningScreen(graph: AppGraph) {
    val vm: WarningViewModel = viewModel {
        WarningViewModel(
            graph.warningSource,
            WarningAnnouncer(graph.warningSource, graph.speechOutput, graph.driveRuntime),
        )
    }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            !ui.available -> Text("경고 상태를 확인할 수 없어요", style = MaterialTheme.typography.headlineLarge)
            ui.guides.isEmpty() -> {
                Text("확인된 경고가 없어요", style = MaterialTheme.typography.headlineLarge)
                Text(ui.source.label(), style = MaterialTheme.typography.labelMedium)
            }
            else -> {
                ui.guides.firstOrNull { it.severity != Severity.INFO }?.let { top ->
                    Card(Modifier.fillMaxWidth(), colors = severityColors(top.severity)) {
                        Text(
                            "${mark(top.severity)} · ${top.title}",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                Text(ui.source.label(), style = MaterialTheme.typography.labelMedium)
                DrivingGate(graph.driveRuntime) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ui.guides.forEach { GuideCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideCard(guide: WarningGuide) {
    Card(Modifier.fillMaxWidth(), colors = severityColors(guide.severity)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${mark(guide.severity)} · ${guide.title}", style = MaterialTheme.typography.headlineLarge)
            Text(guide.meaning, style = MaterialTheme.typography.titleLarge)
            guide.parkedSteps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.headlineLarge)
            }
        }
    }
}

/** 색만으로 심각도를 구분하지 않도록 기호와 글자를 함께 씁니다. */
private fun mark(severity: Severity): String {
    val glyph = when (severity) {
        Severity.DANGER -> "⚠"
        Severity.CAUTION -> "▲"
        Severity.INFO -> "ⓘ"
    }
    return "$glyph ${WarningGuidance.severityLabel(severity)}"
}

@Composable
private fun severityColors(severity: Severity): CardColors = when (severity) {
    Severity.DANGER -> CardDefaults.cardColors(containerColor = AppColors.HyundaiBlue, contentColor = AppColors.White)
    Severity.CAUTION -> CardDefaults.cardColors(containerColor = AppColors.ActiveBlue, contentColor = AppColors.Black)
    Severity.INFO -> CardDefaults.cardColors()
}
