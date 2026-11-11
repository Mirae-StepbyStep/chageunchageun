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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mirae.chageunchageun.domain.mirror.MirrorGuide
import com.mirae.chageunchageun.domain.mirror.MirrorStep
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.DrivingGate
import com.mirae.chageunchageun.ui.theme.AppColors

/**
 * A3 미러 조절 안내 화면(필수).
 * 전체를 DrivingGate로 감싸서, 주행이 시작되면 그림과 애니메이션이 화면에서 빠집니다.
 */
@Composable
fun MirrorScreen(graph: AppGraph) {
    val vm: MirrorViewModel = viewModel { MirrorViewModel() }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("출발 전 미러 맞추기", style = MaterialTheme.typography.displaySmall)
        DrivingGate(graph.driveRuntime) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Box(Modifier.weight(1.2f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    MirrorPicture(ui.step, ui.animated)
                }
                StepPanel(ui, vm, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun StepPanel(ui: MirrorUi, vm: MirrorViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepDots(ui.index, ui.total)
        Text(ui.step.title, style = MaterialTheme.typography.headlineLarge)
        Text(ui.step.body, style = MaterialTheme.typography.headlineSmall, maxLines = 3)
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (ui.canGoBack) {
                OutlinedButton(onClick = vm::previous, modifier = Modifier.weight(1f).heightIn(min = 76.dp)) {
                    Text("이전", style = MaterialTheme.typography.titleLarge)
                }
            }
            Button(
                onClick = if (ui.isLast) vm::restart else vm::next,
                modifier = Modifier.weight(1f).heightIn(min = 76.dp),
            ) {
                Text(if (ui.isLast) "처음부터 다시" else "다음", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun StepDots(index: Int, total: Int) {
    val on = MaterialTheme.colorScheme.secondary
    val off = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(Modifier.size(if (i == index) 18.dp else 12.dp).clip(CircleShape).background(if (i <= index) on else off))
        }
        Text("${index + 1} / $total", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
}

/** 단계별 그림. 거울 단계는 "어긋난 위치 → 맞는 위치"를 반복해 보여 줍니다. */
@Composable
private fun MirrorPicture(step: MirrorStep, animated: Boolean) {
    val misalignment = if (animated) {
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
    val line = MaterialTheme.colorScheme.onBackground

    Canvas(Modifier.fillMaxWidth().aspectRatio(1.5f)) {
        when (step) {
            MirrorStep.POSTURE -> drawPosture(line)
            MirrorStep.ROOM -> drawRoomMirror(misalignment, line)
            MirrorStep.LEFT -> drawSideMirror(misalignment, line, bodyOnRight = true)
            MirrorStep.RIGHT -> drawSideMirror(misalignment, line, bodyOnRight = false)
            MirrorStep.DONE -> drawDone()
        }
    }
}

private fun DrawScope.dashed() = Stroke(
    width = 5.dp.toPx(),
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 10.dp.toPx())),
)

private fun roundRectPath(rect: Rect, radius: Float) =
    Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius))) }

/** 옆에서 본 운전 자세: 좌석, 사람, 핸들. */
private fun DrawScope.drawPosture(line: Color) {
    val w = size.width
    val h = size.height
    fun p(x: Float, y: Float) = Offset(w * x, h * y)
    val seat = Stroke(18.dp.toPx(), cap = StrokeCap.Round)
    val body = 14.dp.toPx()
    // 좌석
    drawLine(AppColors.ActiveBlue, p(0.27f, 0.18f), p(0.33f, 0.80f), seat.width, StrokeCap.Round)
    drawLine(AppColors.ActiveBlue, p(0.33f, 0.80f), p(0.60f, 0.80f), seat.width, StrokeCap.Round)
    // 사람
    drawCircle(line, radius = h * 0.085f, center = p(0.36f, 0.17f))
    drawLine(line, p(0.37f, 0.32f), p(0.41f, 0.70f), body, StrokeCap.Round)
    drawLine(line, p(0.41f, 0.70f), p(0.60f, 0.68f), body, StrokeCap.Round)
    drawLine(line, p(0.60f, 0.68f), p(0.68f, 0.92f), body, StrokeCap.Round)
    drawLine(line, p(0.38f, 0.38f), p(0.52f, 0.50f), body, StrokeCap.Round)
    drawLine(line, p(0.52f, 0.50f), p(0.66f, 0.42f), body, StrokeCap.Round)
    // 핸들
    drawLine(line, p(0.65f, 0.30f), p(0.71f, 0.54f), 10.dp.toPx(), StrokeCap.Round)
    drawLine(line, p(0.68f, 0.42f), p(0.80f, 0.50f), 8.dp.toPx(), StrokeCap.Round)
}

/** 룸미러: 뒷유리(흰색)가 거울 가운데 기준 틀(점선)에 들어옵니다. */
private fun DrawScope.drawRoomMirror(misalignment: Float, line: Color) {
    val w = size.width
    val mh = w * 0.36f
    val mirror = Rect(Offset(0f, (size.height - mh) / 2f), Size(w, mh))
    val radius = mh * 0.3f
    val window = Size(w * 0.62f, mh * 0.6f)
    val target = Offset(mirror.center.x - window.width / 2f, mirror.center.y - window.height / 2f)
    val shift = Offset(w * 0.2f, -mh * 0.32f) * misalignment

    clipPath(roundRectPath(mirror, radius)) {
        drawRect(AppColors.HyundaiBlue, mirror.topLeft, mirror.size)
        drawRoundRect(AppColors.White, target + shift, window, CornerRadius(mh * 0.12f))
        drawRoundRect(AppColors.ActiveBlue, target, window, CornerRadius(mh * 0.12f), style = dashed())
    }
    drawRoundRect(line, mirror.topLeft, mirror.size, CornerRadius(radius), style = Stroke(6.dp.toPx()))
}

/**
 * 사이드미러: 뒷문 손잡이가 차체 쪽 아래 모서리(점선 원)에 오도록 풍경이 움직입니다.
 * [bodyOnRight]가 참이면 왼쪽 미러(차체가 거울 오른쪽에 보임)입니다.
 */
private fun DrawScope.drawSideMirror(misalignment: Float, line: Color, bodyOnRight: Boolean) {
    val mh = size.height
    val mw = mh * 1.25f
    val left = (size.width - mw) / 2f
    val mirror = Rect(Offset(left, 0f), Size(mw, mh))
    val radius = mh * 0.16f

    // 거울 안 좌표(0~1). x는 차체 쪽이 1입니다.
    fun p(x: Float, y: Float) = Offset(left + mw * (if (bodyOnRight) x else 1f - x), mh * y)

    val targetX = 0.84f
    val targetY = 0.82f
    val hx = targetX - 0.27f * misalignment
    val hy = targetY - 0.30f * misalignment
    val horizon = hy - 0.38f

    clipPath(roundRectPath(mirror, radius)) {
        // 하늘과 땅
        drawRect(AppColors.White, mirror.topLeft, mirror.size)
        val groundTop = mh * horizon
        drawRect(AppColors.Sand, Offset(left, groundTop), Size(mw, (mh - groundTop).coerceAtLeast(0f)))
        drawLine(AppColors.Black, Offset(left, groundTop), Offset(left + mw, groundTop), 3.dp.toPx())
        // 옆 차선
        drawLine(
            AppColors.Black, p(hx - 0.42f, horizon), p(hx - 0.80f, hy + 0.5f), 4.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 14.dp.toPx())),
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
        // 뒷문 손잡이
        val handle = Size(mw * 0.13f, mh * 0.045f)
        val center = p(hx, hy)
        drawRoundRect(
            AppColors.White,
            Offset(center.x - handle.width / 2f, center.y - handle.height / 2f),
            handle,
            CornerRadius(handle.height / 2f),
        )
        // 맞출 자리
        drawCircle(AppColors.ActiveBlue, radius = mh * 0.11f, center = p(targetX, targetY), style = dashed())
    }
    drawRoundRect(line, mirror.topLeft, mirror.size, CornerRadius(radius), style = Stroke(6.dp.toPx()))
}

private fun DrawScope.drawDone() {
    val r = size.height * 0.36f
    val c = center
    drawCircle(AppColors.ActiveBlue, r, c)
    val check = Path().apply {
        moveTo(c.x - r * 0.45f, c.y + r * 0.02f)
        lineTo(c.x - r * 0.10f, c.y + r * 0.36f)
        lineTo(c.x + r * 0.48f, c.y - r * 0.30f)
    }
    drawPath(check, AppColors.Black, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
}
