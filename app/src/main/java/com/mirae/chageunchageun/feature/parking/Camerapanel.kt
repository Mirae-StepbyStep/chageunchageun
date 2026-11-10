package com.mirae.chageunchageun.feature.parking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.mirae.chageunchageun.core.model.Gear

private val CamNavy = Color(0xFF002C5F)
private val CamWhite = Color(0xFFFFFFFF)
private val CamPanel = Color(0xFF1B1B1B)
private val CamScene = Color(0xFF2E2E2E)

/**
 * 카메라 영역. 서라운드뷰와 후방 카메라를 위아래로 동시에 보여줍니다.
 * 지금은 시연용 그림만 그립니다.
 *
 * Pleos SDK 문서에서 서드파티 앱이 카메라 영상을 받는 API를 확인하지 못했습니다.
 * 실제 영상 연동이 확인되면 각 CameraView 안의 그리기 부분만 영상 뷰로 바꾸면 됩니다.
 * 이 화면은 실제 카메라 영상이 아니므로 항상 안내 문구를 띄웁니다.
 */
@Composable
fun CameraPanel(
    gear: Gear?,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = CamNavy,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "시연 화면 · 실제 카메라 영상이 아닙니다",
                    color = CamWhite,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                )
            }

            CameraView(
                label = "서라운드뷰",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { drawSurroundScene() }

            CameraView(
                label = if (gear == Gear.R) "후방 카메라 · 후진 중" else "후방 카메라",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { drawRearScene() }
        }
    }
}

@Composable
private fun CameraView(
    label: String,
    modifier: Modifier = Modifier,
    draw: DrawScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CamScene),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            draw()
        }

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
    }
}

/** 후방 카메라 느낌: 주차선과 거리 가이드선. */
private fun DrawScope.drawRearScene() {
    val w = size.width
    val h = size.height
    val top = h * 0.35f
    val stroke = 5.dp.toPx()

    fun leftX(y: Float) = lerp(w * 0.38f, w * 0.12f, (y - top) / (h - top))
    fun rightX(y: Float) = lerp(w * 0.62f, w * 0.88f, (y - top) / (h - top))

    drawLine(Color.White, Offset(leftX(top), top), Offset(leftX(h), h), stroke)
    drawLine(Color.White, Offset(rightX(top), top), Offset(rightX(h), h), stroke)
    drawLine(Color.White, Offset(leftX(top), top), Offset(rightX(top), top), stroke)

    listOf(
        0.52f to Color(0xFF43A047),
        0.68f to Color(0xFFFFC107),
        0.84f to Color(0xFFE53935),
    ).forEach { (fraction, color) ->
        val y = h * fraction
        drawLine(color, Offset(leftX(y), y), Offset(rightX(y), y), 4.dp.toPx())
    }
}

/** 서라운드뷰 느낌: 위에서 본 차량과 주차칸, 옆 장애물. */
private fun DrawScope.drawSurroundScene() {
    val w = size.width
    val h = size.height
    val stroke = 5.dp.toPx()
    val gap = w * 0.2f

    drawLine(Color.White, Offset(w / 2 - gap, h * 0.08f), Offset(w / 2 - gap, h * 0.92f), stroke)
    drawLine(Color.White, Offset(w / 2 + gap, h * 0.08f), Offset(w / 2 + gap, h * 0.92f), stroke)
    drawLine(Color.White, Offset(w / 2 - gap, h * 0.08f), Offset(w / 2 + gap, h * 0.08f), stroke)

    val carWidth = w * 0.22f
    val carHeight = h * 0.62f
    drawRoundRect(
        color = Color(0xFFB0B7C3),
        topLeft = Offset(w / 2 - carWidth / 2, h * 0.24f),
        size = Size(carWidth, carHeight),
        cornerRadius = CornerRadius(14.dp.toPx()),
    )

    drawRect(
        color = Color(0xFF8D6E63),
        topLeft = Offset(w / 2 + gap + 12.dp.toPx(), h * 0.42f),
        size = Size(w * 0.06f, h * 0.2f),
    )
}