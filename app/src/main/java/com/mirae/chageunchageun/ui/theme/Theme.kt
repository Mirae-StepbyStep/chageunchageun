package com.mirae.chageunchageun.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** PRD 앱 디자인 룰의 5색. 다른 색은 쓰지 않습니다. */
object AppColors {
    val HyundaiBlue = Color(0xFF002C5F)
    val Sand = Color(0xFFF6F3F2)
    val ActiveBlue = Color(0xFF00AAD2) // 흰 글자 금지(대비 부족). 검정 글자만.
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
}

private val Light = lightColorScheme(
    primary = AppColors.HyundaiBlue, onPrimary = AppColors.White,
    secondary = AppColors.ActiveBlue, onSecondary = AppColors.Black,
    background = AppColors.Sand, onBackground = AppColors.Black,
    surface = AppColors.Sand, onSurface = AppColors.Black,
    surfaceContainerHighest = AppColors.White, // 카드 배경
    surfaceContainer = AppColors.White, // 내비게이션 레일
)

// 야간 배색은 PRD 미결 사항입니다. 확정 전까지 현대블루 배경 + 흰 글자로 둡니다.
private val Dark = darkColorScheme(
    primary = AppColors.ActiveBlue, onPrimary = AppColors.Black,
    secondary = AppColors.ActiveBlue, onSecondary = AppColors.Black,
    background = AppColors.HyundaiBlue, onBackground = AppColors.White,
    surface = AppColors.HyundaiBlue, onSurface = AppColors.White,
    surfaceContainerHighest = AppColors.Black,
    surfaceContainer = AppColors.HyundaiBlue,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
