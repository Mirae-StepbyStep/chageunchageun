package com.mirae.chageunchageun.feature.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mirae.chageunchageun.domain.practice.DriveGame
import com.mirae.chageunchageun.domain.practice.GameEventType
import com.mirae.chageunchageun.domain.practice.GameInput
import com.mirae.chageunchageun.domain.practice.GameOutcome
import com.mirae.chageunchageun.domain.practice.GameState
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.ui.common.GuideButton
import com.mirae.chageunchageun.ui.common.GuideOutlinedButton
import com.mirae.chageunchageun.ui.common.StatusDot
import com.mirae.chageunchageun.ui.common.keepWords
import com.mirae.chageunchageun.ui.theme.AppColors
import kotlin.math.ceil
import kotlin.math.sin

/**
 * A5 도로 주행 게임 화면(초안). 내 차를 뒤에서 본 시점(TPS)으로 도로를 그립니다.
 * 3D 엔진 없이 Canvas에 원근만 계산해 그립니다. 부르는 쪽에서 DrivingGate와 CarScale로 감쌉니다.
 */
@Composable
fun PracticeGameScreen(graph: AppGraph, onExit: () -> Unit) {
    val vm: PracticeGameViewModel = viewModel { PracticeGameViewModel(graph.controls, graph.steering) }
    val state by vm.state.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    var timeSec by remember { mutableFloatStateOf(0f) }

    // 이 화면이 열릴 때마다(처음, 주행 뒤 다시 정차, 다시 들어옴) 시작 카드부터 합니다.
    // 프레임마다 게임을 진행하고, 화면이 빠지면 멈춥니다.
    LaunchedEffect(Unit) {
        vm.reset()
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    vm.tick(dt)
                    timeSec += dt
                }
                last = now
            }
        }
    }

    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(32.dp)).background(AppColors.Sand)) {
        Canvas(Modifier.fillMaxSize()) { drawScene(state, input, timeSec) }

        // 위 한 줄: 속도, 안내, 나가기. 터널 안에서도 보이게 모두 흰 바탕에 둡니다.
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SpeedCapsule(state.speedMps)
                Banner(DriveGame.prompt(state, input), signal = signalBadge(state), modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(AppColors.White)) {
                    OnWhite { GuideOutlinedButton("목록으로", onExit) }
                }
            }
            ProgressBar(state.distanceM / DriveGame.FINISH_M)
        }

        // 아래 한 줄: 조작부 상태
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ControlChip("핸들", steerChip(input.steerDeg), Modifier.weight(1f))
            ControlChip("브레이크", switchChip(input.brake), Modifier.weight(1f))
            ControlChip("전조등", switchChip(input.lowBeam), Modifier.weight(1f))
            ControlChip("와이퍼", switchChip(input.wiper), Modifier.weight(1f))
        }

        // 카드가 뜰 때는 뒤 장면을 어둡게 해서 카드가 또렷이 보이게 합니다.
        if (phase == GamePhase.Ready || state.finished) {
            Box(Modifier.fillMaxSize().background(AppColors.Black.copy(alpha = 0.45f)))
        }
        when (val p = phase) {
            GamePhase.Ready -> StartCard(input, onStart = vm::begin, onExit = onExit, modifier = Modifier.align(Alignment.Center))
            is GamePhase.Countdown -> CountdownNumber(p.secondsLeft, Modifier.align(Alignment.Center))
            GamePhase.Running -> if (state.finished) {
                ResultCard(state, input, onRestart = vm::begin, onExit = onExit, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** 조작부 칩에 보여 줄 상태. [available]이 거짓이면 신호를 못 받은 것입니다(OFF와 구분). */
private data class ChipState(val text: String, val on: Boolean, val available: Boolean = true)

private val NoSignal = ChipState("확인 불가", on = false, available = false)

private fun switchChip(value: Boolean?): ChipState = when (value) {
    true -> ChipState("ON", on = true)
    false -> ChipState("OFF", on = false)
    null -> NoSignal
}

/** 핸들은 켜고 끄는 조작이 아니라서 방향으로 보여 줍니다. */
private fun steerChip(deg: Float?): ChipState = when {
    deg == null -> NoSignal
    deg <= -5f -> ChipState("◀ 왼쪽", on = true)
    deg >= 5f -> ChipState("오른쪽 ▶", on = true)
    else -> ChipState("가운데", on = false)
}

/** 정지 신호가 다가올 때 안내 옆에 고정해서 보여 줄 표시. 없으면 null입니다. */
private fun signalBadge(state: GameState): Boolean? {
    val index = DriveGame.currentEvent(state) ?: return null
    if (DriveGame.track[index].type != GameEventType.STOP_SIGNAL) return null
    return DriveGame.isStopSignalOn(state, index)
}

@Composable
private fun OnWhite(content: @Composable () -> Unit) {
    // 흰 바탕 위 요소는 야간 배색에서도 글자가 흰색이 되지 않게 색을 고정합니다.
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onBackground = AppColors.Black), content = content)
}

@Composable
private fun SpeedCapsule(speedMps: Float) {
    Row(
        Modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(AppColors.White).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${(speedMps * 3.6f).toInt()}",
            Modifier.width(52.dp),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Black,
            textAlign = TextAlign.End,
        )
        Text("km/h", fontSize = 18.sp, color = AppColors.Black)
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.White.copy(alpha = 0.7f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(AppColors.ActiveBlue))
    }
}

@Composable
private fun Banner(text: String, signal: Boolean?, modifier: Modifier) {
    Row(
        modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(AppColors.White).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        if (signal != null) {
            // 정지는 검은 바탕, 출발은 액티브블루 바탕. 색만으로 구분하지 않게 글자를 함께 씁니다.
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (signal) AppColors.Black else AppColors.ActiveBlue)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    if (signal) "정지" else "출발",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (signal) AppColors.White else AppColors.Black,
                )
            }
        }
        Text(text.keepWords(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AppColors.Black, maxLines = 2)
    }
}

@Composable
private fun ControlChip(name: String, state: ChipState, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val fill = when {
        state.on -> AppColors.ActiveBlue
        state.available -> AppColors.White
        else -> AppColors.White.copy(alpha = 0.7f)
    }
    Row(
        modifier
            .height(64.dp)
            .clip(shape)
            .background(fill)
            .then(if (state.available) Modifier else Modifier.border(2.dp, AppColors.Black.copy(alpha = 0.45f), shape))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            Modifier.weight(1f),
            fontSize = 22.sp,
            color = AppColors.Black.copy(alpha = if (state.on) 1f else 0.6f),
            maxLines = 1,
        )
        Text(
            state.text,
            fontSize = if (state.available) 26.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Black.copy(alpha = if (state.available) 1f else 0.6f),
            maxLines = 1,
        )
    }
}

/** 시작 전 준비 카드. 손이 화면에서 조작부로 옮겨 갈 시간을 줍니다. */
@Composable
private fun StartCard(input: GameInput, onStart: () -> Unit, onExit: () -> Unit, modifier: Modifier) {
    Column(
        modifier.width(680.dp).clip(RoundedCornerShape(28.dp)).background(AppColors.White).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("주행 게임", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = AppColors.Black)
        Text("화면 속 차는 스스로 달립니다. 상황에 맞게 실제 레버와 페달을 써 보세요.", fontSize = 22.sp, color = AppColors.Black)
        listOf(
            "기어는 P에 둡니다. 변속 레버와 가속 페달은 건드리지 않습니다.",
            "두 손은 핸들에, 오른발은 브레이크 위에 올려주세요.",
            "전조등·와이퍼·바퀴가 실제로 움직입니다. 차 주변에 사람이 없는지 확인해주세요.",
        ).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusDot(active = true, Modifier.padding(top = 9.dp))
                Text(line.keepWords(), fontSize = 22.sp, color = AppColors.Black)
            }
        }
        val stillOn = listOfNotNull("전조등".takeIf { input.lowBeam == true }, "와이퍼".takeIf { input.wiper == true })
        if (stillOn.isNotEmpty()) {
            Text(
                "켜져 있음: ${stillOn.joinToString(", ")}. 먼저 꺼주세요.",
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Sand).padding(14.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Black,
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OnWhite { GuideOutlinedButton("목록으로", onExit, Modifier.weight(1f)) }
            GuideButton("시작", onStart, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CountdownNumber(secondsLeft: Float, modifier: Modifier) {
    Box(
        modifier.size(180.dp).clip(RoundedCornerShape(90.dp)).background(AppColors.White),
        contentAlignment = Alignment.Center,
    ) {
        Text("${ceil(secondsLeft).toInt().coerceAtLeast(1)}", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = AppColors.Black)
    }
}

@Composable
private fun ResultCard(state: GameState, input: GameInput, onRestart: () -> Unit, onExit: () -> Unit, modifier: Modifier) {
    Column(
        modifier.width(600.dp).clip(RoundedCornerShape(28.dp)).background(AppColors.White).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("주행 완료!", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = AppColors.Black)
        DriveGame.track.forEachIndexed { i, event ->
            val outcome = state.outcomes[i]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusDot(active = outcome == GameOutcome.GOOD)
                Text(event.type.title, Modifier.weight(1f), fontSize = 24.sp, color = AppColors.Black)
                Text(
                    when (outcome) {
                        GameOutcome.GOOD -> "성공"
                        GameOutcome.MISSED -> "다시 도전"
                        GameOutcome.UNCHECKED, null -> "확인 불가"
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Black,
                )
            }
        }
        // 실제 차의 전조등과 와이퍼를 켠 채로 끝내지 않게 합니다.
        val stillOn = listOfNotNull("전조등".takeIf { input.lowBeam == true }, "와이퍼".takeIf { input.wiper == true })
        Text(
            if (stillOn.isEmpty()) "핸들은 가운데, 전조등은 원래 위치, 와이퍼는 OFF인지 확인해주세요." else "아직 켜져 있음: ${stillOn.joinToString(", ")}. 꺼주세요.",
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Sand).padding(14.dp),
            fontSize = 20.sp,
            fontWeight = if (stillOn.isEmpty()) FontWeight.Normal else FontWeight.Bold,
            color = AppColors.Black,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OnWhite { GuideOutlinedButton("목록으로", onExit, Modifier.weight(1f)) }
            GuideButton("다시 하기", onRestart, Modifier.weight(1f))
        }
    }
}

// ── 그림 ────────────────────────────────────────────────
// 원근: 앞쪽 거리 d(m)에 있는 것은 s = K / (d + K) 배로 작아지고 지평선 쪽으로 올라갑니다.

private const val CAMERA_K = 9f
private const val FAR_M = 170f
private const val ROAD_HALF = 1.3f // 도로 가장자리의 가로 위치(차가 움직이는 범위는 -1~1)

private class Camera(val w: Float, val h: Float, val camX: Float) {
    val horizon = h * 0.40f
    val roadHalfPx = w * 0.46f

    fun scale(d: Float) = CAMERA_K / (d.coerceAtLeast(0f) + CAMERA_K)

    /** 도로 위 한 점(앞쪽 거리 [d], 가로 위치 [x])의 화면 좌표. */
    fun ground(d: Float, x: Float): Offset {
        val s = scale(d)
        return Offset(w / 2f + (x - camX) * roadHalfPx * s, horizon + (h - horizon) * s)
    }

    /** 높이 1(도로 반폭 기준)이 그 거리에서 몇 픽셀인지. */
    fun unit(d: Float) = roadHalfPx * scale(d)
}

private fun DrawScope.quad(color: Color, a: Offset, b: Offset, c: Offset, d: Offset) {
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }, color)
}

private fun DrawScope.drawScene(state: GameState, input: GameInput, timeSec: Float) {
    val cam = Camera(size.width, size.height, state.carX * 0.85f)
    val road = Color(0xFF000000).copy(alpha = 0.78f)

    // 하늘과 땅
    drawRect(AppColors.White, Offset.Zero, Size(size.width, cam.horizon))
    // 흰 안내 칸이 하늘에 묻히지 않게 하늘에 옅은 푸른 기를 줍니다.
    drawRect(AppColors.HyundaiBlue.copy(alpha = 0.14f), Offset.Zero, Size(size.width, cam.horizon))
    drawRect(AppColors.Sand, Offset(0f, cam.horizon), Size(size.width, size.height - cam.horizon))
    drawRect(AppColors.Black.copy(alpha = 0.12f), Offset(0f, cam.horizon), Size(size.width, size.height * 0.012f))

    // 도로
    quad(road, cam.ground(0f, -ROAD_HALF), cam.ground(FAR_M, -ROAD_HALF), cam.ground(FAR_M, ROAD_HALF), cam.ground(0f, ROAD_HALF))
    for (edge in listOf(-ROAD_HALF + 0.08f, ROAD_HALF - 0.08f)) {
        drawLine(AppColors.White, cam.ground(0f, edge), cam.ground(FAR_M, edge), size.width * 0.004f)
    }
    // 차선(점선)과 길가 기둥: 달린 거리만큼 다가옵니다.
    val dashGap = 12f
    val offset = state.distanceM % dashGap
    for (k in 0..14) {
        val d0 = k * dashGap - offset
        val d1 = d0 + 4.5f
        if (d1 <= 0f) continue
        for (lane in listOf(-0.43f, 0.43f)) {
            drawLine(
                AppColors.White, cam.ground(d0.coerceAtLeast(0f), lane), cam.ground(d1, lane),
                strokeWidth = (cam.unit((d0 + d1) / 2f) * 0.035f).coerceAtLeast(1f),
            )
        }
        if (d0 > 0f && k % 2 == 0) {
            for (side in listOf(-ROAD_HALF - 0.35f, ROAD_HALF + 0.35f)) {
                val base = cam.ground(d0, side)
                val u = cam.unit(d0)
                drawLine(AppColors.HyundaiBlue, base, Offset(base.x, base.y - u * 0.45f), (u * 0.06f).coerceAtLeast(1f), StrokeCap.Round)
            }
        }
    }

    // 터널 드나들 때 화면이 한순간에 바뀌지 않게, 입구 앞뒤 [TUNNEL_FADE_M]에 걸쳐 서서히 바꿉니다.
    val tunnel = DriveGame.track.firstOrNull { it.type == GameEventType.TUNNEL && state.distanceM < it.endM }
    val tunnelDark = if (tunnel == null) 0f else {
        val entering = ((state.distanceM - (tunnel.atM - TUNNEL_FADE_M)) / (TUNNEL_FADE_M * 2f)).coerceIn(0f, 1f)
        val leaving = ((tunnel.endM - state.distanceM) / TUNNEL_FADE_M).coerceIn(0f, 1f)
        entering * leaving
    }

    // 상황: 먼 것부터 그려 가까운 것이 위에 오게 합니다.
    DriveGame.track.withIndex().sortedByDescending { it.value.atM }.forEach { (i, event) ->
        val d = event.atM - state.distanceM
        when (event.type) {
            GameEventType.OBSTACLE ->
                if (d in -1f..FAR_M && state.outcomes[i] != GameOutcome.UNCHECKED) drawObstacle(cam, d, event.laneX)
            GameEventType.STOP_SIGNAL -> if (d in -1f..FAR_M) drawStopSignal(cam, d, stop = DriveGame.isStopSignalOn(state, i))
            GameEventType.TUNNEL -> if (d in 0f..FAR_M) drawTunnelEntrance(cam, d, alpha = 1f - tunnelDark)
            GameEventType.RAIN -> Unit
        }
    }

    // 터널 안: 전조등을 켜야 앞이 잘 보입니다. 꺼져 있어도 차선은 희미하게 보이게 둡니다.
    if (tunnel != null && tunnelDark > 0f) {
        val lit = input.lowBeam == true
        drawRect(AppColors.Black.copy(alpha = (if (lit) 0.40f else 0.70f) * tunnelDark))
        val exit = tunnel.endM - state.distanceM
        if (exit in 0f..FAR_M) {
            val u = cam.unit(exit)
            val l = cam.ground(exit, -ROAD_HALF)
            val r = cam.ground(exit, ROAD_HALF)
            drawRoundRect(
                AppColors.White.copy(alpha = tunnelDark), Offset(l.x, l.y - u * 1.5f), Size(r.x - l.x, u * 1.5f), CornerRadius(u * 0.5f),
            )
        }
        if (lit) {
            val beam = AppColors.White.copy(alpha = 0.26f * tunnelDark)
            quad(beam, cam.ground(1f, state.carX - 0.3f), cam.ground(45f, state.carX - 0.9f), cam.ground(45f, state.carX + 0.9f), cam.ground(1f, state.carX + 0.3f))
        }
    }

    // 비: 차보다 먼저 흐림을 그려 차는 또렷하게 둡니다. 와이퍼를 켜면 맑아집니다.
    val raining = DriveGame.isRaining(state)
    if (raining) drawRect(AppColors.White.copy(alpha = if (input.wiper == true) 0.08f else 0.40f))

    drawCar(cam, state, input)

    if (raining) drawRain(wiperOn = input.wiper == true, timeSec = timeSec)
}

private const val TUNNEL_FADE_M = 10f

/** 공사 구간: 줄무늬 가림막. */
private fun DrawScope.drawObstacle(cam: Camera, d: Float, laneX: Float) {
    val dd = d.coerceAtLeast(0f)
    val u = cam.unit(dd)
    val base = cam.ground(dd, laneX)
    val w = u * 0.62f
    val h = u * 0.36f
    val topLeft = Offset(base.x - w / 2f, base.y - h)
    drawLine(AppColors.Black, Offset(base.x - w * 0.38f, base.y), Offset(base.x - w * 0.38f, base.y - h), u * 0.04f)
    drawLine(AppColors.Black, Offset(base.x + w * 0.38f, base.y), Offset(base.x + w * 0.38f, base.y - h), u * 0.04f)
    drawRoundRect(AppColors.ActiveBlue, topLeft, Size(w, h * 0.6f), CornerRadius(u * 0.03f))
    for (i in 0 until 4) {
        val x = topLeft.x + w * (0.08f + i * 0.25f)
        quad(
            AppColors.Black,
            Offset(x, topLeft.y + h * 0.6f), Offset(x + w * 0.1f, topLeft.y),
            Offset(x + w * 0.2f, topLeft.y), Offset(x + w * 0.1f, topLeft.y + h * 0.6f),
        )
    }
}

/**
 * 정지 신호: 굵은 정지선과 도로 위 가로형 신호등(왼쪽 정지, 오른쪽 출발).
 * 색을 5색만 쓰므로 정지는 흰 등, 출발은 액티브블루 등으로 구분하고, 화면 위 안내에 "정지/출발" 글자를 함께 둡니다.
 */
private fun DrawScope.drawStopSignal(cam: Camera, d: Float, stop: Boolean) {
    val dd = d.coerceAtLeast(0f)
    val u = cam.unit(dd)
    drawLine(AppColors.White, cam.ground(dd, -ROAD_HALF + 0.1f), cam.ground(dd, ROAD_HALF - 0.1f), (u * 0.18f).coerceAtLeast(3f))
    val post = cam.ground(dd, ROAD_HALF + 0.15f)
    val top = Offset(post.x, post.y - u * 1.35f)
    val arm = Offset(cam.ground(dd, 0f).x, top.y)
    drawLine(AppColors.Black, post, top, (u * 0.06f).coerceAtLeast(2f))
    drawLine(AppColors.Black, top, arm, (u * 0.06f).coerceAtLeast(2f))
    val r = (u * 0.16f).coerceAtLeast(10.dp.toPx())
    val box = Size(r * 5.2f, r * 2.8f)
    val boxTopLeft = Offset(arm.x - box.width / 2f, arm.y - box.height / 2f)
    drawRoundRect(AppColors.Black, boxTopLeft, box, CornerRadius(r * 0.7f))
    val stopLamp = Offset(arm.x - r * 1.3f, arm.y)
    val goLamp = Offset(arm.x + r * 1.3f, arm.y)
    drawCircle(if (stop) AppColors.White else AppColors.White.copy(alpha = 0.15f), r, stopLamp)
    drawCircle(if (stop) AppColors.White.copy(alpha = 0.15f) else AppColors.ActiveBlue, r, goLamp)
    if (stop) {
        // 정지 등 안의 가로 막대(진입 금지 표시)
        drawLine(AppColors.Black, Offset(stopLamp.x - r * 0.55f, stopLamp.y), Offset(stopLamp.x + r * 0.55f, stopLamp.y), r * 0.35f)
    }
}

/** 터널 입구: 벽과 밝은 테두리의 입구. 가까워지면 [alpha]가 줄어 서서히 사라집니다. */
private fun DrawScope.drawTunnelEntrance(cam: Camera, d: Float, alpha: Float) {
    if (alpha <= 0f) return
    val u = cam.unit(d)
    val l = cam.ground(d, -ROAD_HALF - 2.2f)
    val r = cam.ground(d, ROAD_HALF + 2.2f)
    val wallH = u * 2.6f
    drawRect(AppColors.HyundaiBlue.copy(alpha = alpha), Offset(l.x, l.y - wallH), Size(r.x - l.x, wallH))
    val ol = cam.ground(d, -ROAD_HALF)
    val or = cam.ground(d, ROAD_HALF)
    val openH = u * 1.5f
    val corner = CornerRadius(u * 0.5f)
    drawRoundRect(AppColors.Black.copy(alpha = alpha), Offset(ol.x, ol.y - openH), Size(or.x - ol.x, openH), corner)
    drawRoundRect(
        AppColors.White.copy(alpha = alpha), Offset(ol.x, ol.y - openH), Size(or.x - ol.x, openH), corner,
        style = Stroke((u * 0.05f).coerceAtLeast(1f)),
    )
}

/**
 * 뒤에서 본 내 차. 무광 흰색 덩어리(클레이 모형) 느낌으로, 흰 차체에 검정 음영만 얹습니다.
 * 브레이크를 밟으면 뒤 등이 켜지고, 핸들을 돌리면 살짝 기웁니다.
 */
private fun DrawScope.drawCar(cam: Camera, state: GameState, input: GameInput) {
    val steer = ((input.steerDeg ?: 0f) / DriveGame.FULL_STEER_DEG).coerceIn(-1f, 1f)
    val cw = size.width * 0.23f
    val ch = cw * 0.74f
    // 카메라가 차를 조금 늦게 따라가서, 가장자리로 갈수록 차가 화면 옆으로 치우칩니다.
    val cx = size.width / 2f + (state.carX - cam.camX) * cam.roadHalfPx * 0.75f
    val bottom = size.height * 0.84f
    val left = cx - cw / 2f
    val top = bottom - ch
    fun shade(alpha: Float) = AppColors.Black.copy(alpha = alpha)

    // 바닥 그림자
    val shadowCenter = Offset(cx, bottom + ch * 0.02f)
    drawOval(
        Brush.radialGradient(listOf(shade(0.50f), shade(0f)), shadowCenter, cw * 0.62f),
        Offset(cx - cw * 0.62f, shadowCenter.y - ch * 0.16f), Size(cw * 1.24f, ch * 0.32f),
    )

    rotate(steer * 3.5f, Offset(cx, bottom)) {
        // 바퀴: 차체 아래로 조금만 보입니다.
        val wheel = Size(cw * 0.17f, ch * 0.24f)
        val wheelCorner = CornerRadius(cw * 0.05f)
        drawRoundRect(shade(0.88f), Offset(left + cw * 0.04f, bottom - wheel.height), wheel, wheelCorner)
        drawRoundRect(shade(0.88f), Offset(left + cw * 0.79f, bottom - wheel.height), wheel, wheelCorner)

        // 캐빈: 위가 좁고 모서리가 둥근 덩어리
        val cabinBottom = top + ch * 0.52f
        val cabin = Path().apply {
            moveTo(left + cw * 0.07f, cabinBottom)
            cubicTo(left + cw * 0.10f, top + ch * 0.16f, left + cw * 0.16f, top, left + cw * 0.30f, top)
            lineTo(left + cw * 0.70f, top)
            cubicTo(left + cw * 0.84f, top, left + cw * 0.90f, top + ch * 0.16f, left + cw * 0.93f, cabinBottom)
            close()
        }
        drawPath(cabin, AppColors.White)
        drawPath(cabin, Brush.verticalGradient(listOf(shade(0f), shade(0.10f)), top, cabinBottom))
        // 뒷유리: 무광이라 반사 없이 옅은 음영만
        val glass = Path().apply {
            moveTo(left + cw * 0.17f, top + ch * 0.44f)
            cubicTo(left + cw * 0.19f, top + ch * 0.20f, left + cw * 0.23f, top + ch * 0.09f, left + cw * 0.33f, top + ch * 0.09f)
            lineTo(left + cw * 0.67f, top + ch * 0.09f)
            cubicTo(left + cw * 0.77f, top + ch * 0.09f, left + cw * 0.81f, top + ch * 0.20f, left + cw * 0.83f, top + ch * 0.44f)
            close()
        }
        drawPath(glass, shade(0.16f))

        // 하체: 둥근 상자
        val bodyTop = top + ch * 0.44f
        val bodySize = Size(cw, ch * 0.46f)
        val bodyCorner = CornerRadius(cw * 0.12f)
        drawRoundRect(AppColors.White, Offset(left, bodyTop), bodySize, bodyCorner)
        drawRoundRect(
            Brush.verticalGradient(listOf(shade(0f), shade(0.14f)), bodyTop, bodyTop + bodySize.height),
            Offset(left, bodyTop), bodySize, bodyCorner,
        )
        // 양옆으로 둥글게 넘어가는 면
        drawRoundRect(
            Brush.horizontalGradient(
                0f to shade(0.14f), 0.18f to shade(0f), 0.82f to shade(0f), 1f to shade(0.14f),
                startX = left, endX = left + cw,
            ),
            Offset(left, bodyTop), bodySize, bodyCorner,
        )
        // 어깨선 아래 접힌 면과 범퍼 아래 띠
        drawRoundRect(shade(0.07f), Offset(left + cw * 0.05f, bodyTop + ch * 0.20f), Size(cw * 0.90f, ch * 0.04f), CornerRadius(ch * 0.02f))
        drawRoundRect(shade(0.20f), Offset(left + cw * 0.10f, bodyTop + ch * 0.36f), Size(cw * 0.80f, ch * 0.07f), CornerRadius(ch * 0.035f))

        // 뒤 등: 평소에는 옅은 음영, 브레이크를 밟으면 액티브블루로 켜집니다.
        val braking = input.brake == true
        val lampSize = Size(cw * 0.22f, ch * 0.075f)
        val lampY = bodyTop + ch * 0.08f
        for (lampX in listOf(left + cw * 0.05f, left + cw * 0.73f)) {
            if (braking) {
                val glowCenter = Offset(lampX + lampSize.width / 2f, lampY + lampSize.height / 2f)
                drawCircle(
                    Brush.radialGradient(listOf(AppColors.ActiveBlue.copy(alpha = 0.55f), AppColors.ActiveBlue.copy(alpha = 0f)), glowCenter, lampSize.width * 0.9f),
                    lampSize.width * 0.9f, glowCenter,
                )
            }
            drawRoundRect(if (braking) AppColors.ActiveBlue else shade(0.24f), Offset(lampX, lampY), lampSize, CornerRadius(lampSize.height / 2f))
        }
    }
}

/** 빗줄기. 와이퍼가 움직이면 옅어지고 와이퍼 팔이 화면 아래쪽을 오갑니다. */
private fun DrawScope.drawRain(wiperOn: Boolean, timeSec: Float) {
    val streak = AppColors.HyundaiBlue.copy(alpha = if (wiperOn) 0.25f else 0.45f)
    val len = size.height * 0.07f
    for (i in 0 until 28) {
        // 정해진 자리에서 반복해 떨어지는 빗줄기(프레임마다 같은 무늬가 내려옵니다).
        val x = ((i * 137) % 1000) / 1000f * size.width
        val phase = ((i * 71) % 100) / 100f
        val y = ((timeSec * 1.6f + phase) % 1f) * (size.height + len) - len
        drawLine(streak, Offset(x, y), Offset(x - len * 0.25f, y + len), 3.dp.toPx(), StrokeCap.Round)
    }
    if (wiperOn) {
        val pivot = Offset(size.width / 2f, size.height * 1.04f)
        rotate(sin(timeSec * 4.5f) * 58f, pivot) {
            drawLine(AppColors.Black.copy(alpha = 0.8f), pivot, Offset(pivot.x, size.height * 0.62f), size.width * 0.007f, StrokeCap.Round)
        }
    }
}
