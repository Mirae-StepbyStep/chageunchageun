package com.mirae.chageunchageun.feature.mirror

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.mirae.chageunchageun.domain.mirror.MirrorGuide
import com.mirae.chageunchageun.domain.mirror.MirrorStep
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.CarScale
import com.mirae.chageunchageun.ui.common.DrivingGate
import com.mirae.chageunchageun.ui.common.GuideButton
import com.mirae.chageunchageun.ui.common.GuideOutlinedButton
import com.mirae.chageunchageun.ui.common.StepDots
import com.mirae.chageunchageun.ui.common.keepWords
import com.mirae.chageunchageun.ui.theme.AppColors

/**
 * A3 미러 조절 안내 화면(필수).
 * 전체를 DrivingGate로 감싸서, 주행이 시작되면 그림과 애니메이션이 화면에서 빠집니다.
 */
@Composable
fun MirrorScreen(graph: AppGraph, onDone: () -> Unit = {}) {
    val vm: MirrorViewModel = viewModel { MirrorViewModel() }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            PicturePanel(ui, Modifier.weight(1.25f).fillMaxHeight())
            StepPanel(
                ui = ui,
                vm = vm,
                // 다 본 뒤 나가면 다음에 들어올 때 처음부터 보이게 합니다.
                onDone = { vm.restart(); onDone() },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }

    CarScale {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 화면 이름은 작게 두고, 단계 제목이 가장 크게 보이게 합니다.
            Text(
                "출발 전 미러 맞추기",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            // 완료 화면은 움직이는 그림이 없어 주행 중에도 그대로 둡니다("정차 후에…"로 바뀌지 않게).
            if (ui.isLast) content() else DrivingGate(graph.driveRuntime, content)
        }
    }
}

@Composable
private fun StepPanel(ui: MirrorUi, vm: MirrorViewModel, onDone: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepDots(ui.index, ui.total)
        Text(ui.step.title, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
        Text(ui.step.body.keepWords(), style = MaterialTheme.typography.headlineMedium, lineHeight = 42.sp)
        if (ui.step.howTo.isNotEmpty()) {
            Text(
                ui.step.howTo.keepWords(),
                style = MaterialTheme.typography.titleLarge,
                lineHeight = 32.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        if (ui.step.doneWhen.isNotEmpty()) {
            Text(
                ui.step.doneWhen.keepWords(),
                style = MaterialTheme.typography.titleLarge,
                lineHeight = 32.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.weight(1f))
        // 왼쪽 자리를 항상 비워 두어, 단계가 바뀌어도 주 버튼 위치가 움직이지 않습니다.
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) {
                when {
                    ui.isLast -> GuideOutlinedButton("다시 보기", vm::restart, Modifier.fillMaxWidth())
                    ui.canGoBack -> GuideOutlinedButton("이전", vm::previous, Modifier.fillMaxWidth())
                }
            }
            GuideButton(
                // 앱은 거울이 맞았는지 알 수 없어, 거울 단계에서는 운전자가 직접 확인하고 넘어갑니다.
                text = when {
                    ui.isLast -> "홈으로"
                    ui.animated -> "확인했습니다"
                    else -> "다음"
                },
                onClick = if (ui.isLast) onDone else vm::next,
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}

/** 그림에 쓰는 색. 야간 배색에서는 하늘과 땅을 어둡게 바꿔 큰 흰 면이 눈부시지 않게 합니다. */
private class Scene(
    val line: Color,
    val sky: Color,
    val ground: Color,
    val lane: Color,
    val body: Color,
    val interior: Color,
)

@Composable
private fun scene(): Scene {
    val line = MaterialTheme.colorScheme.onSurface
    return if (isSystemInDarkTheme()) {
        Scene(
            line = line,
            sky = AppColors.HyundaiBlue,
            ground = AppColors.White.copy(alpha = 0.14f),
            lane = AppColors.White.copy(alpha = 0.12f),
            body = AppColors.White.copy(alpha = 0.30f),
            interior = AppColors.Black,
        )
    } else {
        Scene(
            line = line,
            sky = AppColors.White,
            ground = AppColors.Sand,
            lane = AppColors.HyundaiBlue.copy(alpha = 0.18f),
            body = AppColors.HyundaiBlue,
            interior = AppColors.HyundaiBlue,
        )
    }
}

/** 왼쪽 그림 영역. 카드 안에 그림을 두고, 거울 단계에서는 지금 그림이 어느 상태인지 아래에 적습니다. */
@Composable
private fun PicturePanel(ui: MirrorUi, modifier: Modifier) {
    val scene = scene()
    Column(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (ui.animated) {
            // 단계가 바뀌면 애니메이션을 처음(어긋난 상태)부터 다시 시작합니다.
            key(ui.step) {
                val measurer = rememberTextMeasurer()
                if (ui.step == MirrorStep.ROOM) {
                    // 룸미러는 한 번만 보여 줍니다: 어긋난 채 잠깐 멈췄다가 약 1.5초 동안 점선 안으로 들어가고 멈춥니다.
                    // 시스템 애니메이션 배율이 0이면 움직이지 않고 맞은 상태만 보여 줍니다.
                    val context = LocalContext.current
                    val animatorScale = remember {
                        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
                    }
                    val slide = remember { Animatable(1f) }
                    LaunchedEffect(Unit) {
                        if (animatorScale > 0f) {
                            delay(ROOM_HOLD_MS)
                            slide.animateTo(0f, tween(ROOM_SLIDE_MS))
                        } else {
                            slide.snapTo(0f)
                        }
                    }
                    Canvas(Modifier.fillMaxWidth().weight(1f)) { drawRoomMirror(slide.value, scene, measurer) }
                    StatusPill(aligned = slide.value == 0f, hint = "흰 뒷유리를 점선 안에 맞춰주세요")
                } else {
                    val transition = rememberInfiniteTransition(label = "mirror")
                    val progress by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(MirrorGuide.LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
                        label = "progress",
                    )
                    val misalignment = MirrorGuide.misalignment(progress)
                    Canvas(Modifier.fillMaxWidth().weight(1f).graphicsLayer { alpha = MirrorGuide.fade(progress) }) {
                        when (ui.step) {
                            MirrorStep.ROOM -> drawRoomMirror(misalignment, scene, measurer)
                            MirrorStep.LEFT -> drawSideMirror(misalignment, scene, bodyOnRight = true)
                            else -> drawSideMirror(misalignment, scene, bodyOnRight = false)
                        }
                    }
                    StatusPill(aligned = misalignment == 0f)
                }
            }
        } else {
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                if (ui.step == MirrorStep.DONE) drawDone() else drawPosture(scene.line)
            }
        }
    }
}

/** 그림 아래 상태 줄. 맞은 순간이 곁눈으로도 보이게 줄 전체의 색과 표시를 바꿉니다. */
@Composable
private fun StatusPill(aligned: Boolean, hint: String = "점선 자리에 오게 맞춰주세요") {
    val scheme = MaterialTheme.colorScheme
    val line = scheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (aligned) scheme.secondary.copy(alpha = 0.22f) else line.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.size(28.dp)) {
            val r = size.minDimension / 2f
            if (aligned) {
                drawCircle(AppColors.ActiveBlue, r)
                val check = Path().apply {
                    moveTo(center.x - r * 0.45f, center.y + r * 0.02f)
                    lineTo(center.x - r * 0.10f, center.y + r * 0.36f)
                    lineTo(center.x + r * 0.48f, center.y - r * 0.30f)
                }
                drawPath(check, AppColors.Black, style = Stroke(r * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawCircle(line.copy(alpha = 0.6f), r * 0.85f, style = dashed(r * 0.16f))
            }
        }
        Text(
            if (aligned) "이렇게 보이면 맞습니다" else hint,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = line,
        )
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
    val box = fitRect(1.15f)
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
private fun DrawScope.drawRoomMirror(misalignment: Float, scene: Scene, measurer: TextMeasurer) {
    val line = scene.line
    val box = fitRect(1.9f)
    val w = box.width
    val stalk = box.height * 0.16f
    val mirror = Rect(Offset(box.left, box.top + stalk), Size(w, box.height - stalk))
    val mh = mirror.height
    val radius = mh * 0.28f
    val window = Size(w * 0.60f, mh * 0.56f)
    val target = Offset(mirror.center.x - window.width / 2f, mirror.center.y - window.height / 2f)
    val shift = Offset(w * 0.18f, -mh * 0.30f) * misalignment
    val aligned = misalignment == 0f
    // 그림 안 이름표. 거울 크기에 맞춰 키우되 12sp보다 작아지지 않게 합니다.
    val labelPx = maxOf(mh * 0.085f, 12.sp.toPx())
    val pad = mh * 0.04f
    fun label(text: String, color: Color) =
        measurer.measure(text, TextStyle(color = color, fontSize = labelPx.toSp(), fontWeight = FontWeight.SemiBold))

    // 천장에 붙는 지지대
    drawLine(line, Offset(mirror.center.x, box.top), Offset(mirror.center.x, mirror.top), mh * 0.12f, StrokeCap.Round)
    clipPath(roundRectPath(mirror, radius)) {
        // 차 안(어두운 실내)
        drawRect(scene.interior, mirror.topLeft, mirror.size)
        // 뒷유리로 보이는 바깥: 하늘과 도로
        val glass = Rect(target + shift, window)
        clipPath(roundRectPath(glass, mh * 0.10f)) {
            drawRect(scene.sky, glass.topLeft, glass.size)
            val horizon = glass.top + glass.height * 0.55f
            drawRect(scene.ground, Offset(glass.left, horizon), Size(glass.width, glass.bottom - horizon))
            drawLine(line.copy(alpha = 0.6f), Offset(glass.left, horizon), Offset(glass.right, horizon), mh * 0.012f)
            val road = Path().apply {
                moveTo(glass.center.x - glass.width * 0.05f, horizon)
                lineTo(glass.center.x + glass.width * 0.05f, horizon)
                lineTo(glass.center.x + glass.width * 0.30f, glass.bottom)
                lineTo(glass.center.x - glass.width * 0.30f, glass.bottom)
                close()
            }
            drawPath(road, scene.lane)
            drawText(label("뒷유리", line), topLeft = glass.topLeft + Offset(pad, pad * 0.5f))
        }
        // 뒷좌석 머리 받침
        val rest = Size(w * 0.09f, mh * 0.30f)
        for (dx in listOf(-0.19f, 0.19f)) {
            drawRoundRect(
                AppColors.White.copy(alpha = 0.22f),
                Offset(mirror.center.x + w * dx - rest.width / 2f, mirror.bottom - rest.height * 0.85f) + shift,
                rest,
                CornerRadius(rest.width * 0.35f),
            )
        }
        val headrests = label("뒷좌석 머리받침", AppColors.White)
        drawText(
            headrests,
            topLeft = Offset(mirror.center.x - headrests.size.width / 2f, mirror.bottom - headrests.size.height - pad) + shift,
        )
        // 맞출 자리. 뒷유리가 들어오기 전에는 점선 왼쪽에 "여기로"를 적습니다.
        if (!aligned) {
            val here = label("여기로", AppColors.White)
            drawText(here, topLeft = Offset(target.x - here.size.width - pad, mirror.center.y - here.size.height / 2f))
        }
        drawRoundRect(
            AppColors.ActiveBlue, target, window, CornerRadius(mh * 0.10f),
            style = if (aligned) Stroke(mh * 0.06f) else dashed(mh * 0.03f),
        )
    }
    drawRoundRect(line, mirror.topLeft, mirror.size, CornerRadius(radius), style = Stroke(mh * 0.04f))
}

/**
 * 사이드미러: 뒷문 손잡이가 차체 쪽 아래 모서리(점선 원)에 오도록 풍경이 움직입니다.
 * [bodyOnRight]가 참이면 왼쪽 미러(차체가 거울 오른쪽에 보임)입니다.
 */
private fun DrawScope.drawSideMirror(misalignment: Float, scene: Scene, bodyOnRight: Boolean) {
    val line = scene.line
    val box = fitRect(1.25f)
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

    val targetX = 0.87f
    val targetY = 0.83f
    val hx = targetX - 0.27f * misalignment
    val hy = targetY - 0.30f * misalignment
    val horizon = hy - 0.40f

    // 거울을 차체에 붙이는 받침(차체 쪽 아래)
    val armFrom = if (bodyOnRight) mirror.right - radius * 0.3f else mirror.left + radius * 0.3f
    val armTo = if (bodyOnRight) box.right else box.left
    val mount = Path().apply {
        moveTo(armFrom, box.top + mh * 0.52f)
        lineTo(armTo, box.top + mh * 0.66f)
        lineTo(armTo, box.top + mh * 0.84f)
        lineTo(armFrom, box.top + mh * 0.86f)
        close()
    }
    drawPath(mount, line)

    clipPath(roundRectPath(mirror, radius)) {
        // 하늘과 땅
        drawRect(scene.sky, mirror.topLeft, mirror.size)
        val groundTop = box.top + mh * horizon
        drawRect(scene.ground, Offset(left, groundTop), Size(mw, (mirror.bottom - groundTop).coerceAtLeast(0f)))
        drawLine(line.copy(alpha = 0.6f), Offset(left, groundTop), Offset(left + mw, groundTop), mh * 0.008f)
        // 옆 차로: 멀어질수록 좁아지는 도로와 차선
        val lane = Path().apply {
            val a = p(hx - 0.34f, horizon); val b = p(hx - 0.46f, horizon)
            val c = p(hx - 1.25f, hy + 0.6f); val d = p(hx - 0.30f, hy + 0.6f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
        }
        drawPath(lane, scene.lane)
        drawLine(
            line, p(hx - 0.46f, horizon), p(hx - 1.25f, hy + 0.6f), mh * 0.012f,
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
        drawPath(body, scene.body)
        // 창문과 문 사이 선
        val glass = Path().apply {
            val a = p(hx - 0.165f, hy - 0.50f); val b = p(hx - 0.145f, hy - 0.16f)
            val c = p(hx + 0.8f, hy - 0.16f); val d = p(hx + 0.8f, hy - 0.50f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
        }
        drawPath(glass, AppColors.White.copy(alpha = 0.28f))
        drawLine(AppColors.Black.copy(alpha = 0.5f), p(hx + 0.12f, hy - 0.16f), p(hx + 0.13f, hy + 0.6f), mh * 0.008f)
        // 뒷문 손잡이
        val handle = Size(mw * 0.16f, mh * 0.06f)
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
            style = if (aligned) Stroke(mh * 0.036f) else dashed(mh * 0.018f),
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

/** 룸미러 그림: 어긋난 채 멈춰 있는 시간과 점선 안으로 들어가는 시간(ms). */
private const val ROOM_HOLD_MS = 600L
private const val ROOM_SLIDE_MS = 1_500
