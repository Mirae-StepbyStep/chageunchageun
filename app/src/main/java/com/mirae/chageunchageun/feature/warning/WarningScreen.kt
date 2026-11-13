package com.mirae.chageunchageun.feature.warning

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.provider.Settings
import com.mirae.chageunchageun.R
import com.mirae.chageunchageun.core.model.Severity
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.domain.guidance.WarningGuidance
import com.mirae.chageunchageun.domain.guidance.WarningGuide
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.DrivingGate
import com.mirae.chageunchageun.ui.common.label
import com.mirae.chageunchageun.ui.theme.AppColors

// PRD: 경고 제목과 조치는 글자 높이 5mm, 보조 텍스트는 3mm 이상.
// 160dpi 기준 1dp ≈ 0.16mm, 한글 글자 높이 ≈ 0.88em 으로 계산한 값입니다. 실제 차량 화면 dpi에 맞춰 조정합니다.
private val TitleSize = 36.sp
private val BodySize = 24.sp
private val LabelSize = 22.sp

/** C1 경고 안내 화면. 주행 중에는 가장 심각한 경고 하나만 크게, 정차 후에는 조치까지 보여 줍니다. */
@Composable
fun WarningScreen(graph: AppGraph) {
    val vm: WarningViewModel = viewModel { WarningViewModel(graph.warningSource, graph.driveRuntime.phase, graph.speechOutput) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    // DrivingGate와 같은 기준입니다. 정차 중에는 아래 상세 카드와 겹치므로 요약 배너를 숨깁니다.
    val canShowDetail by graph.driveRuntime.canShowDetail.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val animatorScale = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    // 주행 규제 중에는 스크롤하지 않습니다.
    val scroll = if (WarningMotion.scrolls(canShowDetail)) Modifier.verticalScroll(rememberScrollState()) else Modifier

    Column(
        Modifier.fillMaxSize().then(scroll).padding(horizontal = 40.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Header(ui)
        when {
            !ui.available -> StatusCard(
                glyph = "?",
                glyphBackground = AppColors.Sand,
                title = "경고 상태를 확인할 수 없어요",
                body = "차량 신호를 받지 못했어요. 계기판 경고등을 확인해 주세요.",
            )
            ui.guides.isEmpty() -> StatusCard(
                glyph = "✓",
                glyphBackground = AppColors.ActiveBlue,
                title = "확인된 경고가 없어요",
                body = "새 경고가 켜지면 음성으로 먼저 알려 드려요.",
            )
            else -> {
                if (!canShowDetail) {
                    ui.guides.firstOrNull { it.severity != Severity.INFO }?.let {
                        GlanceBanner(it, others = ui.guides.size - 1)
                    }
                }
                DrivingGate(graph.driveRuntime) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        val animate = WarningMotion.animatesEntry(canShowDetail, animatorScale)
                        ui.guides.forEach { guide -> GuideCard(guide, animate, onListenAgain = { vm.listenAgain(guide) }) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(ui: WarningUi) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("차량 경고", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        if (ui.available) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ui.guides.groupingBy { it.severity }.eachCount().toSortedMap().forEach { (severity, count) ->
                    SeverityBadge(severity, suffix = " $count")
                }
                Pill(ui.source.label(), AppColors.Sand, AppColors.Black, BorderStroke(2.dp, AppColors.Black))
            }
        }
    }
}

/** 주행 중 한눈에 보는 배너. 터치가 필요 없습니다. */
@Composable
private fun GlanceBanner(guide: WarningGuide, others: Int) {
    val (bg, fg) = severityColors(guide.severity)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
    ) {
        Row(
            Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            TelltaleIcon(guide, tint = fg, size = 88.dp)
            Column(Modifier.weight(1f)) {
                Text("${WarningGuidance.severityLabel(guide.severity)} 경고", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(guide.title, fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp)
            }
            if (others > 0) Text("외 ${others}건", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 정차 후 상세 카드: 심각도 띠, 경고등, 제목, 의미, 번호 매긴 조치, 다시 듣기. */
@Composable
private fun GuideCard(guide: WarningGuide, animate: Boolean, onListenAgain: () -> Unit) {
    val severityColor = severityColors(guide.severity).first
    // 진입 애니메이션이 시작된 뒤 흐른 시간(초). 재생하지 않을 때는 끝난 상태에서 시작합니다.
    val total = EntryAnimation.totalSeconds(guide.parkedSteps.size)
    val elapsed = remember(guide.type) { Animatable(if (animate) 0f else total) }
    LaunchedEffect(guide.type, animate) {
        if (animate) {
            elapsed.animateTo(total, tween((total * 1000).toInt(), easing = LinearEasing))
        } else {
            elapsed.snapTo(total)
        }
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(16.dp).fillMaxHeight().background(severityColor))
            Column(
                Modifier.padding(horizontal = 36.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TelltaleIcon(
                        guide,
                        tint = severityColor,
                        size = 56.dp,
                        modifier = Modifier.graphicsLayer { alpha = EntryAnimation.telltaleAlpha(elapsed.value) },
                    )
                    SeverityBadge(guide.severity)
                }
                Text(guide.title, fontSize = TitleSize, fontWeight = FontWeight.Bold)
                Text(guide.meaning, fontSize = BodySize)
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    guide.parkedSteps.forEachIndexed { index, step ->
                        Box(
                            Modifier.graphicsLayer {
                                val progress = EntryAnimation.stepProgress(elapsed.value, index)
                                alpha = progress
                                translationY = (1f - progress) * 24.dp.toPx()
                            },
                        ) { StepRow(index + 1, step) }
                    }
                }
                if (WarningGuidance.speech(guide) != null) {
                    Button(onClick = onListenAgain, modifier = Modifier.padding(top = 8.dp).heightIn(min = 64.dp)) {
                        Text("다시 듣기", fontSize = BodySize, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** 경고등 아이콘. 공기압은 계기판 기호를 쓰고, 나머지는 심각도 기호를 씁니다. */
@Composable
private fun TelltaleIcon(guide: WarningGuide, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val description = "${guide.title} 경고등"
    if (guide.type == WarningType.TIRE_PRESSURE) {
        Icon(painterResource(R.drawable.ic_telltale_tpms), description, modifier.size(size), tint)
    } else {
        Text(
            glyph(guide.severity),
            color = tint,
            fontSize = size.value.sp,
            modifier = modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).background(AppColors.HyundaiBlue, CircleShape), contentAlignment = Alignment.Center) {
            Text("$number", color = AppColors.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, fontSize = TitleSize, lineHeight = 44.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusCard(glyph: String, glyphBackground: Color, title: String, body: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(
            Modifier.padding(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Box(Modifier.size(88.dp).background(glyphBackground, CircleShape), contentAlignment = Alignment.Center) {
                Text(glyph, color = AppColors.Black, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = TitleSize, fontWeight = FontWeight.Bold)
                Text(body, fontSize = BodySize)
            }
        }
    }
}

/** 색만으로 심각도를 구분하지 않도록 기호와 글자를 함께 씁니다. */
@Composable
private fun SeverityBadge(severity: Severity, suffix: String = "") {
    val (bg, fg) = severityColors(severity)
    Pill("${glyph(severity)} ${WarningGuidance.severityLabel(severity)}$suffix", bg, fg)
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, border: BorderStroke? = null) {
    Surface(shape = RoundedCornerShape(50), color = bg, contentColor = fg, border = border) {
        Text(
            text,
            fontSize = LabelSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}

private fun glyph(severity: Severity): String = when (severity) {
    Severity.DANGER -> "⚠"
    Severity.CAUTION -> "▲"
    Severity.INFO -> "ⓘ"
}

/**
 * 심각도 색(배경, 글자). 위험은 빨강, 주의는 주황, 정보는 액티브블루입니다.
 * 빨강과 주황은 PRD 5색의 예외로 경고 표시에만 씁니다. 글자색은 대비 4.5:1 이상인 쪽입니다.
 */
internal fun severityColors(severity: Severity): Pair<Color, Color> = when (severity) {
    Severity.DANGER -> AppColors.WarningRed to AppColors.White
    Severity.CAUTION -> AppColors.WarningOrange to AppColors.Black
    Severity.INFO -> AppColors.ActiveBlue to AppColors.Black
}
