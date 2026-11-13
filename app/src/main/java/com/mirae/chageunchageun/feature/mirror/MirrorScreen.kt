package com.mirae.chageunchageun.feature.mirror

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mirae.chageunchageun.domain.mirror.MirrorGuide
import com.mirae.chageunchageun.domain.mirror.MirrorStep
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.CarScale
import com.mirae.chageunchageun.ui.common.DrivingGate
import com.mirae.chageunchageun.ui.common.GuideButton
import com.mirae.chageunchageun.ui.common.GuideOutlinedButton
import com.mirae.chageunchageun.ui.common.StatusDot
import com.mirae.chageunchageun.ui.common.StepDots
import com.mirae.chageunchageun.ui.common.keepWords
import com.mirae.chageunchageun.ui.theme.AppColors

/**
 * A3 미러 조절 안내 화면(필수).
 * 전체를 DrivingGate로 감싸서, 주행이 시작되면 그림과 애니메이션이 화면에서 빠집니다.
 */
@Composable
fun MirrorScreen(graph: AppGraph) {
    val vm: MirrorViewModel = viewModel { MirrorViewModel() }
    val ui by vm.ui.collectAsStateWithLifecycle()

    CarScale {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("출발 전 미러 맞추기", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("그림처럼 보이면 맞게 조절된 거예요.", style = MaterialTheme.typography.titleLarge)
            }
            DrivingGate(graph.driveRuntime) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    PicturePanel(ui, Modifier.weight(1.25f).fillMaxHeight())
                    StepPanel(ui, vm, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun StepPanel(ui: MirrorUi, vm: MirrorViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        StepDots(ui.index, ui.total)
        Text(ui.step.title, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Text(ui.step.body.keepWords(), style = MaterialTheme.typography.headlineMedium, lineHeight = 42.sp)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (ui.canGoBack) GuideOutlinedButton("이전", vm::previous, Modifier.weight(1f))
            GuideButton(
                text = if (ui.isLast) "처음부터 다시" else "다음",
                onClick = if (ui.isLast) vm::restart else vm::next,
                modifier = Modifier.weight(if (ui.canGoBack) 1.6f else 1f),
            )
        }
    }
}

/** 왼쪽 그림 영역. 카드 안에 그림을 두고, 거울 단계에서는 지금 그림이 어느 상태인지 아래에 적습니다. */
@Composable
private fun PicturePanel(ui: MirrorUi, modifier: Modifier) {
    val misalignment = if (ui.animated) {
        val transition = rememberInfiniteTransition(label = "mirror")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4_500, easing = LinearEasing), RepeatMode.Restart),
            label = "progress",
        )
        MirrorGuide.misalignment(progress)
    } else {
        0f
    }
    val aligned = misalignment == 0f
    val line = MaterialTheme.colorScheme.onSurface

    Column(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            when (ui.step) {
                MirrorStep.POSTURE -> drawPosture(line)
                MirrorStep.ROOM -> drawRoomMirror(misalignment, line)
                MirrorStep.LEFT -> drawSideMirror(misalignment, line, bodyOnRight = true)
                MirrorStep.RIGHT -> drawSideMirror(misalignment, line, bodyOnRight = false)
                MirrorStep.DONE -> drawDone()
            }
        }
        if (ui.animated) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusDot(active = aligned)
                Text(
                    if (aligned) "이렇게 보이면 맞아요" else "점선 자리에 오도록 조절해요",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}

/** 그림을 그릴 영역 안에서 가로세로 비율 [ratio]를 지키는 가장 큰 사각형. */
private fun DrawScope.fitRect(ratio: Float): Rect {
    val w = minOf(size.width, size.height * ratio)
    val h = w / ratio
    return Rect(Offset((size.width - w) / 2f, (size.height - h) / 2f), Size(w, h))
}

private fun dashed(width: Float) = Stroke(
    width = width,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 3.2f, width * 2f)),
)

private fun roundRectPath(rect: Rect, radius: Float) =
    Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius))) }

/** 옆에서 본 운전 자세: 좌석에 등을 붙이고, 팔을 살짝 굽혀 핸들을 잡은 모습. */
private fun DrawScope.drawPosture(line: Color) {
    val box = fitRect(1.3f)
    fun p(x: Float, y: Float) = Offset(box.left + box.width * x, box.top + box.height * y)
    val u = box.height / 100f
    val seatColor = AppColors.ActiveBlue

    // 바닥
    drawLine(line.copy(alpha = 0.25f), p(0.08f, 0.93f), p(0.92f, 0.93f), 1.2f * u, StrokeCap.Round)
    // 좌석: 머리 받침, 등받이, 방석
    drawLine(seatColor, p(0.235f, 0.10f), p(0.25f, 0.20f), 7f * u, StrokeCap.Round)
    drawLine(seatColor, p(0.255f, 0.27f), p(0.33f, 0.74f), 9f * u, StrokeCap.Round)
    drawLine(seatColor, p(0.33f, 0.78f), p(0.58f, 0.76f), 9f * u, StrokeCap.Round)
    drawLine(seatColor, p(0.40f, 0.84f), p(0.40f, 0.93f), 4f * u, StrokeCap.Round)
    // 사람: 머리, 몸통, 허벅지, 종아리, 발
    drawCircle(line, 7.5f * u, p(0.335f, 0.16f))
    drawLine(line, p(0.345f, 0.32f), p(0.405f, 0.66f), 11f * u, StrokeCap.Round)
    drawLine(line, p(0.42f, 0.67f), p(0.62f, 0.64f), 9f * u, StrokeCap.Round)
    drawLine(line, p(0.62f, 0.64f), p(0.74f, 0.86f), 7.5f * u, StrokeCap.Round)
    drawLine(line, p(0.74f, 0.87f), p(0.81f, 0.85f), 6f * u, StrokeCap.Round)
    // 팔: 팔꿈치를 살짝 굽혀 핸들을 잡습니다.
    drawLine(line, p(0.37f, 0.36f), p(0.50f, 0.52f), 6.5f * u, StrokeCap.Round)
    drawLine(line, p(0.50f, 0.52f), p(0.655f, 0.40f), 6f * u, StrokeCap.Round)
    // 핸들과 대시보드
    drawLine(line, p(0.655f, 0.27f), p(0.70f, 0.52f), 4.5f * u, StrokeCap.Round)
    drawLine(line, p(0.68f, 0.40f), p(0.80f, 0.46f), 3.5f * u, StrokeCap.Round)
    val dash = Path().apply {
        val a = p(0.80f, 0.36f); val b = p(0.93f, 0.36f); val c = p(0.93f, 0.93f); val d = p(0.86f, 0.93f); val e = p(0.80f, 0.56f)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); lineTo(e.x, e.y); close()
    }
    drawPath(dash, line.copy(alpha = 0.25f))
    // 눈높이에서 미러를 보는 시선
    drawLine(
        seatColor, p(0.40f, 0.15f), p(0.86f, 0.15f), 1.6f * u,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * u, 3f * u)),
    )
}

/** 룸미러: 뒷유리(밝은 창)가 거울 가운데 기준 틀(점선)에 들어옵니다. */
private fun DrawScope.drawRoomMirror(misalignment: Float, line: Color) {
    val box = fitRect(2.3f)
    val w = box.width
    val stalk = box.height * 0.16f
    val mirror = Rect(Offset(box.left, box.top + stalk), Size(w, box.height - stalk))
    val mh = mirror.height
    val radius = mh * 0.28f
    val window = Size(w * 0.60f, mh * 0.56f)
    val target = Offset(mirror.center.x - window.width / 2f, mirror.center.y - window.height / 2f)
    val shift = Offset(w * 0.18f, -mh * 0.30f) * misalignment
    val aligned = misalignment == 0f

    // 천장에 붙는 지지대
    drawLine(line, Offset(mirror.center.x, box.top), Offset(mirror.center.x, mirror.top), mh * 0.12f, StrokeCap.Round)
    clipPath(roundRectPath(mirror, radius)) {
        // 차 안(어두운 실내)
        drawRect(AppColors.HyundaiBlue, mirror.topLeft, mirror.size)
        // 뒷유리로 보이는 바깥: 하늘과 도로
        val glass = Rect(target + shift, window)
        clipPath(roundRectPath(glass, mh * 0.10f)) {
            drawRect(AppColors.White, glass.topLeft, glass.size)
            val horizon = glass.top + glass.height * 0.55f
            drawRect(AppColors.Sand, Offset(glass.left, horizon), Size(glass.width, glass.bottom - horizon))
            drawLine(AppColors.Black, Offset(glass.left, horizon), Offset(glass.right, horizon), mh * 0.012f)
            val road = Path().apply {
                moveTo(glass.center.x - glass.width * 0.05f, horizon)
                lineTo(glass.center.x + glass.width * 0.05f, horizon)
                lineTo(glass.center.x + glass.width * 0.30f, glass.bottom)
                lineTo(glass.center.x - glass.width * 0.30f, glass.bottom)
                close()
            }
            drawPath(road, AppColors.HyundaiBlue.copy(alpha = 0.35f))
        }
        // 뒷좌석 머리 받침
        val rest = Size(w * 0.09f, mh * 0.30f)
        for (dx in listOf(-0.19f, 0.19f)) {
            drawRoundRect(
                AppColors.Black,
                Offset(mirror.center.x + w * dx - rest.width / 2f, mirror.bottom - rest.height * 0.85f) + shift,
                rest,
                CornerRadius(rest.width * 0.35f),
            )
        }
        // 맞출 자리
        drawRoundRect(
            AppColors.ActiveBlue, target, window, CornerRadius(mh * 0.10f),
            style = if (aligned) Stroke(mh * 0.035f) else dashed(mh * 0.03f),
        )
    }
    drawRoundRect(line, mirror.topLeft, mirror.size, CornerRadius(radius), style = Stroke(mh * 0.04f))
}

/**
 * 사이드미러: 뒷문 손잡이가 차체 쪽 아래 모서리(점선 원)에 오도록 풍경이 움직입니다.
 * [bodyOnRight]가 참이면 왼쪽 미러(차체가 거울 오른쪽에 보임)입니다.
 */
private fun DrawScope.drawSideMirror(misalignment: Float, line: Color, bodyOnRight: Boolean) {
    val box = fitRect(1.45f)
    val mh = box.height
    val arm = box.width * 0.10f
    val mw = box.width - arm
    // 거울을 차체에 붙이는 팔은 차체 쪽(왼쪽 미러는 오른쪽)에 있습니다.
    val left = if (bodyOnRight) box.left else box.left + arm
    val mirror = Rect(Offset(left, box.top), Size(mw, mh))
    val radius = mh * 0.16f
    val aligned = misalignment == 0f

    // 거울 안 좌표(0~1). x는 차체 쪽이 1입니다.
    fun p(x: Float, y: Float) = Offset(left + mw * (if (bodyOnRight) x else 1f - x), box.top + mh * y)

    val targetX = 0.84f
    val targetY = 0.80f
    val hx = targetX - 0.27f * misalignment
    val hy = targetY - 0.30f * misalignment
    val horizon = hy - 0.40f

    // 팔
    val armY = box.top + mh * 0.62f
    val armFrom = if (bodyOnRight) mirror.right else mirror.left
    val armTo = if (bodyOnRight) box.right else box.left
    drawLine(line, Offset(armFrom, armY), Offset(armTo, armY), mh * 0.16f, StrokeCap.Butt)

    clipPath(roundRectPath(mirror, radius)) {
        // 하늘과 땅
        drawRect(AppColors.White, mirror.topLeft, mirror.size)
        val groundTop = box.top + mh * horizon
        drawRect(AppColors.Sand, Offset(left, groundTop), Size(mw, (mirror.bottom - groundTop).coerceAtLeast(0f)))
        drawLine(AppColors.Black, Offset(left, groundTop), Offset(left + mw, groundTop), mh * 0.008f)
        // 옆 차로: 멀어질수록 좁아지는 도로와 차선
        val lane = Path().apply {
            val a = p(hx - 0.34f, horizon); val b = p(hx - 0.46f, horizon)
            val c = p(hx - 1.25f, hy + 0.6f); val d = p(hx - 0.30f, hy + 0.6f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
        }
        drawPath(lane, AppColors.HyundaiBlue.copy(alpha = 0.18f))
        drawLine(
            AppColors.Black, p(hx - 0.46f, horizon), p(hx - 1.25f, hy + 0.6f), mh * 0.012f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(mh * 0.06f, mh * 0.045f)),
        )
        // 내 차 옆면
        val body = Path().apply {
            val a = p(hx - 0.20f, hy - 0.62f)
            val b = p(hx - 0.12f, hy + 0.6f)
            val c = p(hx + 0.8f, hy + 0.6f)
            val d = p(hx + 0.8f, hy - 0.62f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
        }
        drawPath(body, AppColors.HyundaiBlue)
        // 창문과 문 사이 선
        val glass = Path().apply {
            val a = p(hx - 0.165f, hy - 0.50f); val b = p(hx - 0.145f, hy - 0.16f)
            val c = p(hx + 0.8f, hy - 0.16f); val d = p(hx + 0.8f, hy - 0.50f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
        }
        drawPath(glass, AppColors.White.copy(alpha = 0.28f))
        drawLine(AppColors.Black.copy(alpha = 0.5f), p(hx + 0.12f, hy - 0.16f), p(hx + 0.13f, hy + 0.6f), mh * 0.008f)
        // 뒷문 손잡이
        val handle = Size(mw * 0.13f, mh * 0.05f)
        val center = p(hx, hy)
        drawRoundRect(
            AppColors.White,
            Offset(center.x - handle.width / 2f, center.y - handle.height / 2f),
            handle,
            CornerRadius(handle.height / 2f),
        )
        // 맞출 자리
        drawCircle(
            AppColors.ActiveBlue, radius = mh * 0.11f, center = p(targetX, targetY),
            style = if (aligned) Stroke(mh * 0.022f) else dashed(mh * 0.018f),
        )
    }
    drawRoundRect(line, mirror.topLeft, mirror.size, CornerRadius(radius), style = Stroke(mh * 0.022f))
}

private fun DrawScope.drawDone() {
    val r = size.minDimension * 0.30f
    val c = center
    drawCircle(AppColors.ActiveBlue.copy(alpha = 0.25f), r * 1.25f, c)
    drawCircle(AppColors.ActiveBlue, r, c)
    val check = Path().apply {
        moveTo(c.x - r * 0.45f, c.y + r * 0.02f)
        lineTo(c.x - r * 0.10f, c.y + r * 0.36f)
        lineTo(c.x + r * 0.48f, c.y - r * 0.30f)
    }
    drawPath(check, AppColors.Black, style = Stroke(r * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
