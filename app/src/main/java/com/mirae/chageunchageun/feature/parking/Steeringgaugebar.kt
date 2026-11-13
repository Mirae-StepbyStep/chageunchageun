package com.mirae.chageunchageun.feature.parking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ParkingScreen.kt의 색상은 private이라 같은 값을 따로 둡니다.
private val GaugeNavy = Color(0xFF002C5F)
private val GaugeSand = Color(0xFFF6F3F2)
private val GaugeBlack = Color(0xFF000000)
private val GaugeWhite = Color(0xFFFFFFFF)
private val GaugeGrey = Color(0xFF888780)

// 승인된 브랜드 색에 초록·주황이 없습니다. 시안에서 쓴 색이며 디자인 확인 후 바꾸세요.
val GaugeOnTarget = Color(0xFF1D9E75)
private val GaugeZone = Color(0xFF9FE1CB)
private val WheelMarker = Color(0xFFD85A30)
private val DragHintBg = Color(0xFFFAEEDA)
private val DragHintText = Color(0xFF633806)

/** 현재 위치 마커 색(남색)과 목표 위치 마커 색(초록)을 구분합니다. */
private val CurrentMarker = GaugeNavy
private val TargetMarker = GaugeOnTarget

/**
 * 핸들 카드: 핸들 그림(현재 회전 + 초록 목표 표시), 현재/목표/남은 조작, 목표 구간 게이지.
 *
 * @param angleDeg 현재 핸들 각도. null이면 "정보 없음".
 * @param targetDeg 계산된 목표 핸들 각도. null이면 목표 표시를 숨깁니다.
 */
@Composable
fun SteeringGaugeBar(
    angleDeg: Float?,
    targetDeg: Float?,
    modifier: Modifier = Modifier,
    /** 개발자 테스트용. null이 아니면 게이지를 끌거나 눌러 핸들 각도를 바꿉니다. */
    onDrag: ((Float) -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        color = GaugeWhite,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "핸들",
                    color = GaugeNavy,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (onDrag != null) {
                    Surface(
                        modifier = Modifier.padding(start = 10.dp),
                        color = DragHintBg,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "게이지를 끌어서 조작",
                            color = DragHintText,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            if (angleDeg == null) {
                Text(
                    text = "정보 없음",
                    color = GaugeBlack,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                return@Column
            }

            val onTarget = gaugeState(angleDeg, targetDeg) == GaugeState.ON_TARGET

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SteeringWheelIcon(
                    angleDeg = angleDeg,
                    targetDeg = targetDeg,
                    color = if (onTarget) GaugeOnTarget else GaugeNavy,
                    modifier = Modifier.size(120.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ValueRow(
                        dot = CurrentMarker,
                        label = "현재",
                        value = steeringWithDegText(angleDeg),
                    )
                    if (targetDeg != null) {
                        ValueRow(
                            dot = TargetMarker,
                            label = "목표",
                            value = steeringWithDegText(targetDeg),
                        )
                        Text(
                            text = if (onTarget) {
                                "목표 구간 안 · 핸들 유지"
                            } else {
                                "남은 조작량 ${remainingText(targetDeg - angleDeg)}"
                            },
                            color = if (onTarget) GaugeOnTarget else GaugeNavy,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            TargetZoneBar(
                angleDeg = angleDeg,
                targetDeg = targetDeg,
                onTarget = onTarget,
                onDrag = onDrag,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("왼쪽 끝", color = GaugeBlack, fontSize = 18.sp)
                Text("가운데", color = GaugeBlack, fontSize = 18.sp)
                Text("오른쪽 끝", color = GaugeBlack, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun ValueRow(dot: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(dot, CircleShape),
        )
        Text(
            text = "  $label  ",
            color = GaugeNavy,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = GaugeBlack,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 위에서 본 핸들. 주황 표시는 현재 회전, 바깥 초록 표시는 목표 위치(고정)입니다. */
@Composable
private fun SteeringWheelIcon(
    angleDeg: Float,
    targetDeg: Float?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f * 0.74f
        val c = center
        val ring = r * 0.22f
        val spoke = r * 0.2f

        // 목표 표시: 한 바퀴를 넘는 각도도 바깥 원 위에 방향으로 표시합니다.
        if (targetDeg != null) {
            val a = Math.toRadians((targetDeg - 90f).toDouble())
            val outer = r * 1.3f
            val inner = r * 1.08f
            drawLine(
                color = TargetMarker,
                start = Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                end = Offset(c.x + outer * cos(a).toFloat(), c.y + outer * sin(a).toFloat()),
                strokeWidth = r * 0.18f,
            )
        }

        rotate(degrees = angleDeg, pivot = c) {
            drawCircle(color = color, radius = r, center = c, style = Stroke(width = ring))
            drawLine(color, Offset(c.x - r, c.y + r * 0.1f), Offset(c.x + r, c.y + r * 0.1f), spoke)
            drawLine(color, Offset(c.x, c.y + r * 0.1f), Offset(c.x, c.y + r), spoke)
            drawCircle(color = color, radius = r * 0.26f, center = c)

            val markerW = r * 0.16f
            val markerH = r * 0.36f
            drawRoundRect(
                color = WheelMarker,
                topLeft = Offset(c.x - markerW / 2f, c.y - r - markerH / 2f),
                size = Size(markerW, markerH),
                cornerRadius = CornerRadius(markerW / 3f),
            )
        }
    }
}

/**
 * 가운데가 0인 막대.
 * 연초록 띠 = 목표 구간, 초록 삼각 마커 = 목표 위치, 남색 마커 = 현재 위치,
 * 현재와 목표 사이 점선 구간 = 남은 조작량.
 */
@Composable
private fun TargetZoneBar(
    angleDeg: Float,
    targetDeg: Float?,
    onTarget: Boolean,
    onDrag: ((Float) -> Unit)?,
) {
    val latestOnDrag by rememberUpdatedState(onDrag)
    val dragModifier = if (onDrag == null) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            fun degAt(x: Float): Float {
                val f = (x / size.width).coerceIn(0f, 1f) * 2f - 1f
                return ((f * MAX_STEERING_WHEEL_DEG) / 10f).roundToInt() * 10f
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                latestOnDrag?.invoke(degAt(down.position.x))
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    latestOnDrag?.invoke(degAt(change.position.x))
                    change.consume()
                }
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(dragModifier),
    ) {
        val barTop = size.height * 0.30f
        val barH = size.height * 0.40f
        val half = size.width / 2f
        fun x(deg: Float) = half + steeringFraction(deg) * half

        drawRoundRect(
            color = GaugeSand,
            topLeft = Offset(0f, barTop),
            size = Size(size.width, barH),
            cornerRadius = CornerRadius(10.dp.toPx()),
        )

        drawLine(
            color = GaugeGrey,
            start = Offset(half, barTop - 6.dp.toPx()),
            end = Offset(half, barTop + barH + 6.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
        )

        val cx = x(angleDeg)

        if (targetDeg != null) {
            val z0 = x(max(-MAX_STEERING_WHEEL_DEG, targetDeg - ON_TARGET_DEG))
            val z1 = x(min(MAX_STEERING_WHEEL_DEG, targetDeg + ON_TARGET_DEG))
            drawRect(
                color = GaugeZone,
                topLeft = Offset(z0, barTop),
                size = Size(z1 - z0, barH),
            )

            // 남은 조작량: 현재와 목표 사이.
            val tx = x(targetDeg)
            if (!onTarget) {
                drawRect(
                    color = GaugeNavy.copy(alpha = 0.18f),
                    topLeft = Offset(min(cx, tx), barTop + barH * 0.3f),
                    size = Size(abs(tx - cx), barH * 0.4f),
                )
            }

            // 목표 마커: 위쪽 삼각형 + 세로선.
            drawLine(
                color = TargetMarker,
                start = Offset(tx, barTop - 2.dp.toPx()),
                end = Offset(tx, barTop + barH + 2.dp.toPx()),
                strokeWidth = 5.dp.toPx(),
            )
            val tri = Path().apply {
                val s = 10.dp.toPx()
                moveTo(tx - s, 0f)
                lineTo(tx + s, 0f)
                lineTo(tx, barTop - 2.dp.toPx())
                close()
            }
            drawPath(tri, TargetMarker)
        }

        // 현재 마커: 아래쪽 삼각형 + 세로선.
        val markerColor = if (onTarget) GaugeOnTarget else CurrentMarker
        drawLine(
            color = markerColor,
            start = Offset(cx, barTop - 2.dp.toPx()),
            end = Offset(cx, barTop + barH + 2.dp.toPx()),
            strokeWidth = 6.dp.toPx(),
        )
        val tri = Path().apply {
            val s = 10.dp.toPx()
            moveTo(cx - s, size.height)
            lineTo(cx + s, size.height)
            lineTo(cx, barTop + barH + 2.dp.toPx())
            close()
        }
        drawPath(tri, markerColor)
    }
}
