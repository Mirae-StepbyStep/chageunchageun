package com.mirae.chageunchageun.feature.practice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mirae.chageunchageun.domain.practice.PracticeGuide
import com.mirae.chageunchageun.domain.practice.PracticeItem
import com.mirae.chageunchageun.domain.practice.PracticeReading
import com.mirae.chageunchageun.domain.practice.PracticeSession
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.CarScale
import com.mirae.chageunchageun.ui.common.DrivingGate
import com.mirae.chageunchageun.ui.common.GuideButton
import com.mirae.chageunchageun.ui.common.GuideOutlinedButton
import com.mirae.chageunchageun.ui.common.StatusDot
import com.mirae.chageunchageun.ui.common.StepDots
import com.mirae.chageunchageun.ui.common.keepWords
import com.mirae.chageunchageun.ui.common.label
import com.mirae.chageunchageun.ui.theme.AppColors

/**
 * A5 정차 중 조작 연습 화면(초안).
 * 전체를 DrivingGate로 감싸서, 주행이 시작되면 연습 화면이 빠지고 짧은 안내만 남습니다.
 */
@Composable
fun PracticeScreen(graph: AppGraph) {
    val vm: PracticeViewModel = viewModel { PracticeViewModel(graph.controls, graph.steering) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    // 모의 운전을 하는 중인지. 조작부 익히기(목록·단계 연습)와 화면을 나눕니다.
    var inGame by rememberSaveable { mutableStateOf(false) }

    CarScale {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!inGame) Header(ui, vm)
            DrivingGate(graph.driveRuntime) {
                val session = ui.session
                when {
                    inGame -> PracticeGameScreen(graph, onExit = { inGame = false })
                    session == null -> PracticeList(ui, vm, onStartGame = { inGame = true })
                    else -> PracticeRun(ui, session, vm)
                }
            }
        }
    }
}

@Composable
private fun Header(ui: PracticeUi, vm: PracticeViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("정차 중 조작 연습", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("하나씩 익힌 뒤 모의 운전에서 써 보세요.", style = MaterialTheme.typography.titleLarge)
        }
        LevelToggle(ui.level, vm::setLevel)
    }
}

@Composable
private fun LevelToggle(level: AdvisorLevel, onSelect: (AdvisorLevel) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AdvisorLevel.entries.forEach { option ->
            val selected = option == level
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ── 연습 목록 ─────────────────────────────────────────────

@Composable
private fun PracticeList(ui: PracticeUi, vm: PracticeViewModel, onStartGame: () -> Unit) {
    val items = PracticeGuide.items
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items.forEach { item ->
                ItemCard(
                    item = item,
                    done = item in ui.completed,
                    recommended = item == ui.nextItem,
                    onClick = { vm.open(item) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${ui.completed.size} / ${items.size} 완료",
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (ui.completed.isNotEmpty()) {
                GuideOutlinedButton("기록 지우기", vm::resetAll)
                Spacer(Modifier.size(16.dp))
            }
            if (ui.nextItem != null) {
                GuideOutlinedButton(if (ui.completed.isEmpty()) "연습 시작" else "이어서 연습", vm::openNext)
                Spacer(Modifier.size(16.dp))
            }
            GuideButton("모의 운전", onStartGame)
        }
    }
}

@Composable
private fun ItemCard(
    item: PracticeItem,
    done: Boolean,
    recommended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHighest,
        border = if (recommended) BorderStroke(3.dp, scheme.secondary) else null,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val line = scheme.onSurface
            Canvas(Modifier.fillMaxWidth().weight(1f)) { drawItemIcon(item, active = done, line = line, angleDeg = 0f) }
            Text(item.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(active = done)
                Text(
                    when {
                        done -> "완료"
                        recommended -> "다음 연습"
                        else -> "${item.stages.size}단계"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

// ── 연습 진행 ─────────────────────────────────────────────

@Composable
private fun PracticeRun(ui: PracticeUi, session: PracticeSession, vm: PracticeViewModel) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        StatePanel(ui, session, Modifier.weight(1f).fillMaxHeight())
        Column(Modifier.weight(1.15f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StepDots(session.index, session.stages.size)
            Text(session.item.title, style = MaterialTheme.typography.headlineSmall)
            val stage = session.current
            if (stage == null) {
                Text("연습 완료!", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text("${session.item.title} 조작을 직접 해 봤습니다.", style = MaterialTheme.typography.headlineMedium)
            } else {
                Text(stage.instruction.keepWords(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                session.lastConfirmed?.let { Confirmed(it.confirmed) }
                Tips(session.item, showWhere = ui.level == AdvisorLevel.DETAILED)
                if (ui.reading == PracticeReading.None) {
                    Text(
                        "지금은 자동으로 확인할 수 없습니다. 직접 해 본 뒤 눌러주세요.".keepWords(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            RunButtons(ui, session, vm)
        }
    }
}

@Composable
private fun Confirmed(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusDot(active = true)
        Text(text, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun Tips(item: PracticeItem, showWhere: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 위치 설명은 "자세히"에서만, 주의 문장은 안전과 관련돼 항상 보여 줍니다.
        if (showWhere) Text("위치  ${item.where}".keepWords(), style = MaterialTheme.typography.titleLarge)
        Text("주의  ${item.caution}".keepWords(), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun RunButtons(ui: PracticeUi, session: PracticeSession, vm: PracticeViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GuideOutlinedButton("목록으로", vm::backToList, Modifier.weight(1f))
        val stage = session.current
        when {
            stage == null && ui.nextItem != null -> GuideButton("다음 연습", vm::openNext, Modifier.weight(1f))
            stage == null -> GuideButton("연습 마치기", vm::backToList, Modifier.weight(1f))
            ui.reading == PracticeReading.None -> GuideButton("직접 확인함", vm::confirmManually, Modifier.weight(1f))
            stage.skippable -> GuideButton("건너뛰기", vm::confirmManually, Modifier.weight(1f))
        }
    }
}

/** 왼쪽: 지금 차 상태. 운전자가 조작하면 여기가 바로 바뀝니다. */
@Composable
private fun StatePanel(ui: PracticeUi, session: PracticeSession, modifier: Modifier) {
    val reading = ui.reading
    // 그림은 연습 진행과 상관없이 차의 실제 상태만 따릅니다.
    val active = (reading as? PracticeReading.Switch)?.on == true
    val angle by animateFloatAsState((reading as? PracticeReading.Angle)?.deg ?: 0f, label = "angle")
    val line = MaterialTheme.colorScheme.onSurface

    Column(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("지금 차 상태", style = MaterialTheme.typography.titleLarge)
        Canvas(Modifier.fillMaxWidth().weight(1f)) { drawItemIcon(session.item, active, line, angle) }
        Text(stateText(session.item, reading), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        // 시연 신호를 실제 값처럼 보이지 않게 출처를 항상 함께 적습니다.
        Text("출처: ${ui.source.label()}", style = MaterialTheme.typography.titleMedium)
    }
}

private fun stateText(item: PracticeItem, reading: PracticeReading): String = when (reading) {
    // 켜고 끄는 조작부는 레버에 적힌 대로 ON / OFF로 통일합니다.
    is PracticeReading.Switch -> if (reading.on) "ON" else "OFF"
    is PracticeReading.Angle -> when {
        reading.deg <= -PracticeGuide.CENTER_DEG -> "◀ 왼쪽 ${(-reading.deg).toInt()}°"
        reading.deg >= PracticeGuide.CENTER_DEG -> "오른쪽 ${reading.deg.toInt()}° ▶"
        else -> "가운데"
    }
    // 못 받은 값은 OFF와 구분합니다.
    PracticeReading.None -> "확인 불가"
}

// ── 그림 ────────────────────────────────────────────────
// 초안이라 조작부를 단순한 기호로만 그립니다. 켜지면 액티브블루로 채웁니다.

private fun DrawScope.drawItemIcon(item: PracticeItem, active: Boolean, line: Color, angleDeg: Float) {
    val r = size.minDimension * 0.34f
    val stroke = (r * 0.1f).coerceAtLeast(3.dp.toPx())
    val accent = AppColors.ActiveBlue
    when (item) {
        PracticeItem.LOW_BEAM, PracticeItem.HIGH_BEAM -> drawLamp(r, stroke, active, line, accent, high = item == PracticeItem.HIGH_BEAM)
        PracticeItem.WIPER -> drawWiper(r, stroke, active, line, accent)
        PracticeItem.BRAKE -> drawPedal(r, stroke, active, line, accent)
        PracticeItem.STEERING -> drawWheel(r, stroke, active, line, accent, angleDeg)
    }
}

/** 전조등 기호: 반원 렌즈와 빛줄기. 하향등은 빛이 아래로 기울고, 상향등은 곧게 나갑니다. */
private fun DrawScope.drawLamp(r: Float, stroke: Float, active: Boolean, line: Color, accent: Color, high: Boolean) {
    val c = Offset(center.x + r * 0.45f, center.y)
    if (active) drawArc(accent, 270f, 180f, useCenter = true, topLeft = c - Offset(r * 0.7f, r * 0.7f), size = Size(r * 1.4f, r * 1.4f))
    drawArc(line, 270f, 180f, useCenter = true, topLeft = c - Offset(r * 0.7f, r * 0.7f), size = Size(r * 1.4f, r * 1.4f), style = Stroke(stroke))
    val rayColor = if (active) accent else line.copy(alpha = 0.35f)
    val drop = if (high) 0f else r * 0.28f
    for (i in -2..2) {
        val y = c.y + i * r * 0.3f
        drawLine(rayColor, Offset(c.x - r * 0.2f, y), Offset(c.x - r * 1.5f, y + drop), stroke, StrokeCap.Round)
    }
}

/** 와이퍼 기호: 앞유리 부채꼴과 와이퍼 팔. */
private fun DrawScope.drawWiper(r: Float, stroke: Float, active: Boolean, line: Color, accent: Color) {
    val pivot = Offset(center.x, center.y + r * 0.9f)
    val box = Size(r * 3.2f, r * 3.2f)
    val topLeft = pivot - Offset(r * 1.6f, r * 1.6f)
    if (active) drawArc(accent, 215f, 110f, useCenter = true, topLeft = topLeft, size = box)
    drawArc(line, 215f, 110f, useCenter = true, topLeft = topLeft, size = box, style = Stroke(stroke))
    rotate(if (active) 28f else -50f, pivot) {
        drawLine(line, pivot, Offset(pivot.x, pivot.y - r * 1.5f), stroke * 1.4f, StrokeCap.Round)
    }
}

/** 브레이크 페달 기호: 넓은 페달과 지지대. 밟으면 채워집니다. */
private fun DrawScope.drawPedal(r: Float, stroke: Float, active: Boolean, line: Color, accent: Color) {
    val w = r * 1.5f
    val h = r * 1.0f
    val topLeft = Offset(center.x - w / 2f, center.y - h * 0.1f + if (active) r * 0.18f else 0f)
    val corner = CornerRadius(r * 0.18f)
    drawLine(line, Offset(center.x, center.y - r * 1.1f), Offset(center.x, topLeft.y), stroke * 1.4f, StrokeCap.Round)
    if (active) drawRoundRect(accent, topLeft, Size(w, h), corner)
    drawRoundRect(line, topLeft, Size(w, h), corner, style = Stroke(stroke))
    for (i in 1..3) {
        val y = topLeft.y + h * i / 4f
        drawLine(line, Offset(topLeft.x + w * 0.18f, y), Offset(topLeft.x + w * 0.82f, y), stroke * 0.6f, StrokeCap.Round)
    }
}

/** 핸들 기호: 실제 조향각만큼 돌아갑니다. */
private fun DrawScope.drawWheel(r: Float, stroke: Float, active: Boolean, line: Color, accent: Color, angleDeg: Float) {
    rotate(angleDeg.coerceIn(-180f, 180f), center) {
        drawCircle(if (active) accent else line, r * 1.15f, center, style = Stroke(stroke * 1.8f))
        drawLine(line, Offset(center.x - r * 1.1f, center.y), Offset(center.x + r * 1.1f, center.y), stroke * 1.4f, StrokeCap.Round)
        drawLine(line, center, Offset(center.x, center.y + r * 1.1f), stroke * 1.4f, StrokeCap.Round)
        drawCircle(line, r * 0.28f, center)
        // 위쪽 표시: 핸들이 얼마나 돌았는지 보입니다.
        drawCircle(accent, stroke * 1.2f, Offset(center.x, center.y - r * 1.15f))
    }
}
