package com.mirae.chageunchageun.feature.parking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirae.chageunchageun.core.model.Gear
import kotlin.math.max
import kotlin.math.min

private val CamNavy = Color(0xFF002C5F)
private val CamWhite = Color(0xFFFFFFFF)
private val CamPanel = Color(0xFF1B1B1B)
private val CamScene = Color(0xFF2E2E2E)
private val CarBody = Color(0xFFB0B7C3)
private val NeighborCar = Color(0xFF8D6E63)
private val PathNow = Color(0xFFFFC107)
private val SimBadgeBg = Color(0xFFFAEEDA)
private val SimBadgeText = Color(0xFF633806)

/** 두 카메라 화면이 함께 쓰는 상태. 같은 값으로 그리므로 두 화면의 경로가 항상 일치합니다. */
data class ParkingVisualState(
    val steeringDeg: Float?,
    /** 계산된 목표 경로(차 뒤 모서리 두 줄, 월드 좌표). null이면 그리지 않습니다. */
    val plannedEdges: List<List<Vec2>>?,
    /** 목표 위치의 차 외곽(월드 좌표). */
    val goalOutline: List<Vec2>?,
    val pose: Pose,
    val geometry: VehicleGeometry,
    val scene: ParkingScene,
)

/**
 * 서라운드뷰 + 후방 카메라. [wide]이면 나란히, 아니면 위아래로 놓습니다.
 *
 * 둘 다 실제 카메라 영상이 아닌 그림입니다. Pleos 공개 SDK에서 서드파티 앱이
 * 카메라 영상을 받는 API를 확인하지 못했습니다. 영상 API가 확인되면 배경만 영상으로 바꾸고
 * 경로선 계산(ParkingPathModel)은 그대로 쓰면 됩니다.
 */
@Composable
fun CameraPanel(
    gear: Gear?,
    state: ParkingVisualState,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = CamPanel,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CameraView("서라운드뷰", state.geometry.isAssumed, Modifier.weight(1f).fillMaxHeight()) {
                        drawSurround(state)
                    }
                    CameraView(rearLabel(gear), state.geometry.isAssumed, Modifier.weight(1f).fillMaxHeight()) {
                        drawRear(state)
                    }
                }
            } else {
                CameraView("서라운드뷰", state.geometry.isAssumed, Modifier.fillMaxWidth().weight(1f)) {
                    drawSurround(state)
                }
                CameraView(rearLabel(gear), state.geometry.isAssumed, Modifier.fillMaxWidth().weight(1f)) {
                    drawRear(state)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendLine(PathNow)
                Text("  지금 핸들로 가는 길", color = CamWhite, fontSize = 18.sp)
                Spacer(Modifier.width(20.dp))
                if (state.plannedEdges != null) {
                    LegendLine(GaugeOnTarget)
                    Text("  계산된 목표 경로", color = CamWhite, fontSize = 18.sp)
                }
            }

            Text(
                text = "시연 그림 · 실제 카메라 영상이 아닙니다",
                color = CamWhite.copy(alpha = 0.8f),
                fontSize = 16.sp,
            )
        }
    }
}

private fun rearLabel(gear: Gear?) =
    if (gear == Gear.R) "후방 카메라 · 후진 중" else "후방 카메라"

@Composable
private fun CameraView(
    label: String,
    simulated: Boolean,
    modifier: Modifier = Modifier,
    draw: DrawScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CamScene),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { draw() }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            color = CamNavy,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = label,
                color = CamWhite,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        if (simulated) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                color = SimBadgeBg,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "시뮬레이션 경로",
                    color = SimBadgeText,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun LegendLine(color: Color) {
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(5.dp)
            .background(color),
    )
}

private fun DrawScope.pathStroke(color: Color, dashed: Boolean, widthDp: Int) = Stroke(
    width = widthDp.dp.toPx(),
    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(18f, 14f)) else null,
)

private fun DrawScope.drawPolyline(
    points: List<Offset?>,
    color: Color,
    stroke: Stroke,
) {
    // null(화면 밖) 지점에서 선을 끊습니다.
    var path = Path()
    var started = false
    points.forEach { p ->
        if (p == null) {
            if (started) drawPath(path, color, style = stroke)
            path = Path()
            started = false
        } else if (!started) {
            path.moveTo(p.x, p.y)
            started = true
        } else {
            path.lineTo(p.x, p.y)
        }
    }
    if (started) drawPath(path, color, style = stroke)
}

/* ---------------- 서라운드뷰(위에서 본 그림, 월드 좌표 고정) ---------------- */

private fun DrawScope.drawSurround(state: ParkingVisualState) {
    val g = state.geometry
    val all = state.scene.targetStall.corners +
        state.scene.neighborCars.flatMap { it.corners } +
        vehicleOutline(g).map { vehicleToWorld(Pose(), it) }

    // 장면 전체(시작 위치 기준)가 화면에 들어오게 고정 배율을 씁니다. 차가 움직여도 배율은 그대로입니다.
    val minX = all.minOf { it.x } - 1.2f
    val maxX = all.maxOf { it.x } + 1.2f
    val minY = all.minOf { it.y } - 1.0f
    val maxY = all.maxOf { it.y } + 1.0f
    val scale = min(size.width / (maxX - minX), (size.height - 40.dp.toPx()) / (maxY - minY))
    val ox = size.width / 2f - (minX + maxX) / 2f * scale
    val oy = 40.dp.toPx() + (size.height - 40.dp.toPx()) / 2f + (minY + maxY) / 2f * scale

    fun px(w: Vec2) = Offset(ox + w.x * scale, oy - w.y * scale)

    state.scene.stallLines.forEach { (a, b) ->
        drawLine(CamWhite.copy(alpha = 0.7f), px(a), px(b), 2.dp.toPx())
    }

    val stall = Path().apply {
        state.scene.targetStall.corners.map(::px).forEachIndexed { i, p ->
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(stall, GaugeOnTarget.copy(alpha = 0.22f))
    drawPath(stall, GaugeOnTarget, style = pathStroke(GaugeOnTarget, true, 2))

    state.scene.neighborCars.forEach { box ->
        val p = Path().apply {
            box.corners.map(::px).forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            close()
        }
        drawPath(p, NeighborCar)
    }

    state.goalOutline?.let { outline ->
        val goal = Path().apply {
            outline.map(::px).forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            close()
        }
        drawPath(goal, GaugeOnTarget, style = pathStroke(GaugeOnTarget, true, 2))
    }

    state.plannedEdges?.forEach { edge ->
        drawPolyline(edge.map(::px), GaugeOnTarget, pathStroke(GaugeOnTarget, true, 4))
    }
    state.steeringDeg?.let { deg ->
        reversePathEdges(deg, g).forEach { edge ->
            drawPolyline(edge.map { px(vehicleToWorld(state.pose, it)) }, PathNow, pathStroke(PathNow, false, 5))
        }
    }

    val body = Path().apply {
        vehicleOutline(g).map { px(vehicleToWorld(state.pose, it)) }.forEachIndexed { i, o ->
            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        }
        close()
    }
    drawPath(body, CarBody)
    drawPath(body, CamWhite, style = Stroke(2.dp.toPx()))
    // 앞쪽 표시: 운전자가 차 방향을 바로 알 수 있게.
    val nose = px(vehicleToWorld(state.pose, Vec2(0f, g.wheelbaseM + g.frontOverhangM - 0.4f)))
    drawCircle(CamNavy, radius = 6.dp.toPx(), center = nose)
}

/* ---------------- 후방 카메라(차량 좌표, 원근 투영) ---------------- */

private const val CAM_HEIGHT_M = 1.0f
private const val MIN_DEPTH_M = 0.35f

private fun DrawScope.drawRear(state: ParkingVisualState) {
    val g = state.geometry
    val camY = -g.rearOverhangM
    val f = size.width * 0.42f
    val horizon = size.height * 0.2f

    // 후방 카메라 화면은 거울처럼 보이므로 차의 오른쪽이 화면 오른쪽에 옵니다.
    fun proj(v: Vec2): Offset? {
        val depth = camY - v.y
        if (depth < MIN_DEPTH_M) return null
        return Offset(size.width / 2f + v.x * f / depth, horizon + CAM_HEIGHT_M * f / depth)
    }

    fun segment(a: Vec2, b: Vec2, n: Int = 16): List<Vec2> =
        (0..n).map { i -> Vec2(a.x + (b.x - a.x) * i / n, a.y + (b.y - a.y) * i / n) }

    // 주변 주차선: 월드에 고정된 선을 지금 차 위치에서 본 모습. 차가 움직일 때만 바뀝니다.
    state.scene.stallLines.forEach { (a, b) ->
        drawPolyline(
            segment(worldToVehicle(state.pose, a), worldToVehicle(state.pose, b), 32).map(::proj),
            CamWhite.copy(alpha = 0.55f),
            Stroke(3.dp.toPx()),
        )
    }

    // 고정 거리 가이드(차 폭, 0.5 / 1 / 2m).
    val half = g.widthM / 2f
    listOf(-half, half).forEach { x ->
        drawPolyline(segment(Vec2(x, camY - 0.3f), Vec2(x, camY - 2.2f)).map(::proj), CamWhite, Stroke(3.dp.toPx()))
    }
    listOf(
        0.5f to Color(0xFFE53935),
        1.0f to Color(0xFFFFC107),
        2.0f to Color(0xFF43A047),
    ).forEach { (d, c) ->
        drawPolyline(segment(Vec2(-half, camY - d), Vec2(half, camY - d)).map(::proj), c, Stroke(3.dp.toPx()))
    }

    // 같은 계산 경로를 지금 차 위치에서 본 모습으로 바꿔 그립니다.
    state.plannedEdges?.forEach { edge ->
        drawPolyline(edge.map { proj(worldToVehicle(state.pose, it)) }, GaugeOnTarget, pathStroke(GaugeOnTarget, true, 5))
    }
    state.steeringDeg?.let { deg ->
        reversePathEdges(deg, g, lengthM = 6f).forEach { edge ->
            drawPolyline(edge.map(::proj), PathNow, pathStroke(PathNow, false, 6))
        }
    }

    // 범퍼 가장자리.
    drawRect(
        color = Color.Black.copy(alpha = 0.6f),
        topLeft = Offset(0f, size.height - max(18.dp.toPx(), size.height * 0.06f)),
    )
}
