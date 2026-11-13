package com.mirae.chageunchageun.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 안내 화면을 설계한 기준 높이. 이 높이에서 글자와 버튼 크기를 정했습니다. */
private val DESIGN_HEIGHT = 720.dp

/**
 * 차량 화면에 맞춰 [content] 전체(글자, 버튼, 여백)를 키웁니다.
 * 차량 디스플레이는 화면 밀도가 낮게 잡혀(예: 2560x1440, 160dpi) 기본 크기로는 글자가 작게 보입니다.
 * 화면 높이가 기준 높이보다 크면 그 비율만큼 키우고, 작으면 그대로 둡니다.
 */
@Composable
fun CarScale(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = (maxHeight / DESIGN_HEIGHT).coerceIn(1f, 2.5f)
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
            content()
        }
    }
}

/** 단계 진행 표시: 지난 단계는 채우고, 지금 단계는 길게 보여 줍니다. */
@Composable
fun StepDots(index: Int, total: Int, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.secondary
    val off = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(
                Modifier
                    .height(14.dp)
                    .width(if (i == index) 48.dp else 14.dp)
                    .clip(CircleShape)
                    .background(if (i <= index) on else off),
            )
        }
        Text(
            "${(index + 1).coerceAtMost(total)} / $total",
            Modifier.padding(start = 8.dp),
            fontSize = 22.sp,
        )
    }
}

private val ButtonHeight = 72.dp
private val ButtonShape = RoundedCornerShape(20.dp)
private val ButtonPadding = PaddingValues(horizontal = 28.dp)

/** 화면의 주된 행동 버튼. 운전석에서 누르기 쉽게 크게 둡니다. */
@Composable
fun GuideButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier.height(ButtonHeight), shape = ButtonShape, contentPadding = ButtonPadding) {
        Text(text, fontSize = 24.sp, maxLines = 1)
    }
}

/** 보조 행동 버튼(이전, 목록으로 등). */
@Composable
fun GuideOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick,
        modifier.height(ButtonHeight),
        shape = ButtonShape,
        contentPadding = ButtonPadding,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
    ) {
        Text(text, fontSize = 24.sp, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** 작은 상태 점. 켜짐·맞음 같은 상태 표시에 씁니다. */
@Composable
fun StatusDot(active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
    Box(modifier.size(14.dp).clip(CircleShape).background(color))
}

/**
 * 한글 문장이 단어 중간에서 줄바꿈되지 않게 합니다("모서/리에" 대신 "모서리에"를 다음 줄로).
 * 단어 안 글자 사이에 줄바꿈 금지 문자(U+2060)를 넣습니다.
 */
fun String.keepWords(): String =
    split(' ').joinToString(" ") { word -> word.toList().joinToString("\u2060") }
