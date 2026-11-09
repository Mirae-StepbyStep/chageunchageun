package com.example.chaguenchaguen.runtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.chaguenchaguen.BuildConfig
import com.example.chaguenchaguen.demo.DebugPanel
import com.example.chaguenchaguen.feature.home.HomeScreen
import com.example.chaguenchaguen.feature.onboarding.OnboardingScreen
import com.example.chaguenchaguen.feature.parking.ParkingScreen
import com.example.chaguenchaguen.feature.report.ReportScreen
import com.example.chaguenchaguen.feature.seat.SeatScreen
import com.example.chaguenchaguen.feature.voice.VoiceScreen
import com.example.chaguenchaguen.feature.warning.WarningScreen
import com.example.chaguenchaguen.ui.theme.AppTheme

/** 앱의 화면 목록. 새 화면은 여기에 한 줄 추가합니다(PR 리뷰). */
enum class AppScreen(val label: String, val debugOnly: Boolean = false) {
    HOME("홈"),
    ONBOARDING("A4"),
    SEAT("A1 시트"),
    PARKING("B1 주차"),
    WARNING("C1 경고"),
    REPORT("D1 회고"),
    VOICE("E2 음성"),
    DEBUG("디버그", debugOnly = true),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as ChaguenApp).graph
        setContent { AppTheme { AppRoot(graph) } }
    }
}

@Composable
fun AppRoot(graph: AppGraph) {
    var current by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    val screens = AppScreen.entries.filter { !it.debugOnly || BuildConfig.DEBUG }

    Surface(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                screens.forEach { screen ->
                    NavigationRailItem(
                        selected = current == screen,
                        onClick = { current = screen },
                        icon = {},
                        label = { Text(screen.label) },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (current) {
                    AppScreen.HOME -> HomeScreen(graph)
                    AppScreen.ONBOARDING -> OnboardingScreen(graph)
                    AppScreen.SEAT -> SeatScreen(graph)
                    AppScreen.PARKING -> ParkingScreen(graph)
                    AppScreen.WARNING -> WarningScreen(graph)
                    AppScreen.REPORT -> ReportScreen(graph)
                    AppScreen.VOICE -> VoiceScreen(graph)
                    AppScreen.DEBUG -> DebugPanel(graph)
                }
            }
        }
    }
}
