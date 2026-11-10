package com.mirae.chageunchageun.feature.parking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// ParkingScreen.kt의 색상은 private이라 같은 값을 따로 둡니다.
private val GaugeNavy = Color(0xFF002C5F)
private val GaugeSand = Color(0xFFF6F3F2)
private val GaugeBlack = Color(0xFF000000)
private val GaugeWhite = Color(0xFFFFFFFF)

// 승인된 브랜드 색에 초록이 없습니다. 디자인 확인 후 바꾸세요.
private val GaugeOnTarget = Color(0xFF2E9E5B)

/**
 * 100%로 볼 앞바퀴 최대 조향각(도).
 * 가정값입니다. 개발자 모드 슬라이더 범위(±35°)에 맞췄으니 실제 차종 값으로 교체하세요.
 */
const val MAX_WHEEL_ANGLE_DEG = 35f

private const val ON_TARGET_PCT = 5f
private const val NEAR_PCT = 15f

/** 조향각(도)을 -100..100 퍼센트로 바꿉니다. 음수는 왼쪽, 양수는 오른쪽. */
fun wheelAnglePercent(
    angleDeg: Float,
    maxDeg: Float = MAX_WHEEL_ANGLE_DEG,
): Float = (angleDeg / maxDeg * 100f).coerceIn(-100f, 100f)

/** 목표 대비 현재 상태. 차량 무드램프 연동 등 다른 출력에서도 재사용할 수 있습니다. */
enum class GaugeState { NO_TARGET, APPROACHING, NEAR, ON_TARGET, OVERSHOOT }

fun gaugeState(currentPct: Float, targetPct: Float?): GaugeState {
    if (targetPct == null) return GaugeState.NO_TARGET
    val diff = targetPct - currentPct
    return when {
        abs(diff) <= ON_TARGET_PCT -> GaugeState.ON_TARGET
        abs(diff) <= NEAR_PCT -> GaugeState.NEAR
        currentPct * targetPct > 0f && abs(currentPct) > abs(targetPct) ->
            GaugeState.OVERSHOOT
        else -> GaugeState.APPROACHING
    }
}

private fun gaugeMessage(currentPct: Float, targetPct: Float): String =
    when (gaugeState(currentPct, targetPct)) {
        GaugeState.ON_TARGET -> "좋아요, 거기서 멈춰요"
        GaugeState.NEAR -> "곧 멈춰요"
        GaugeState.OVERSHOOT -> "너무 많이 돌렸어요"
        GaugeState.APPROACHING ->
            if (targetPct > currentPct) "오른쪽으로 더 돌려요" else "왼쪽으로 더 돌려요"
        GaugeState.NO_TARGET -> ""
    }

private fun percentText(pct: Float): String {
    val rounded = abs(pct).roundToInt()
    return when {
        rounded == 0 -> "중앙 0%"
        pct < 0f -> "왼쪽 $rounded%"
        else -> "오른쪽 $rounded%"
    }
}

/**
 * 앞바퀴 조향 퍼센트 게이지.
 *
 * @param angleDeg 현재 앞바퀴 조향각(도). null이면 "정보 없음"을 보여줍니다.
 * @param targetPercent 목표 위치(-100..100). null이면 목표 표시와 안내 문구를 숨깁니다.
 */
@Composable
fun SteeringGaugeBar(
    angleDeg: Float?,
    targetPercent: Float?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = GaugeWhite,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "조향 게이지",
                color = GaugeNavy,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )

            if (angleDeg == null) {
                Text(
                    text = "정보 없음",
                    color = GaugeBlack,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                val pct = wheelAnglePercent(angleDeg)
                val state = gaugeState(pct, targetPercent)
                val fillColor =
                    if (state == GaugeState.ON_TARGET) GaugeOnTarget else GaugeNavy

                Text(
                    text = percentText(pct),
                    color = GaugeBlack,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )

                if (targetPercent != null) {
                    Text(
                        text = gaugeMessage(pct, targetPercent),
                        color = if (state == GaugeState.ON_TARGET) GaugeOnTarget else GaugeNavy,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                GaugeBar(
                    pct = pct,
                    targetPct = targetPercent,
                    fillColor = fillColor,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("왼쪽 100", color = GaugeBlack, fontSize = 16.sp)
                    Text("0", color = GaugeBlack, fontSize = 16.sp)
                    Text("100 오른쪽", color = GaugeBlack, fontSize = 16.sp)
                }

                Text(
                    text = "앞바퀴 %.0f° · 최대 %.0f° 기준"
                        .format(Locale.KOREA, angleDeg, MAX_WHEEL_ANGLE_DEG),
                    color = GaugeNavy,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun GaugeBar(
    pct: Float,
    targetPct: Float?,
    fillColor: Color,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GaugeSand),
    ) {
        val center = size.width / 2f
        val x = center + (pct / 100f) * center

        drawRect(
            color = fillColor,
            topLeft = Offset(min(center, x), 0f),
            size = Size(abs(x - center), size.height),
        )

        drawLine(
            color = GaugeBlack.copy(alpha = 0.35f),
            start = Offset(center, 0f),
            end = Offset(center, size.height),
            strokeWidth = 2.dp.toPx(),
        )

        if (targetPct != null) {
            val tx = center + (targetPct.coerceIn(-100f, 100f) / 100f) * center
            drawLine(
                color = GaugeBlack,
                start = Offset(tx, 0f),
                end = Offset(tx, size.height),
                strokeWidth = 5.dp.toPx(),
            )
        }
    }
}