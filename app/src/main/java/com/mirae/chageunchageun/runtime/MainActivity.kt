package com.mirae.chageunchageun.runtime

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mirae.chageunchageun.BuildConfig
import com.mirae.chageunchageun.demo.DebugPanel
import com.mirae.chageunchageun.demo.SignalCheckScreen
import com.mirae.chageunchageun.feature.home.HomeScreen
import com.mirae.chageunchageun.feature.mirror.MirrorScreen
import com.mirae.chageunchageun.feature.onboarding.OnboardingScreen
import com.mirae.chageunchageun.feature.parking.ParkingScreen
import com.mirae.chageunchageun.feature.practice.PracticeScreen
import com.mirae.chageunchageun.feature.toll.TollScreen
import com.mirae.chageunchageun.feature.voice.VoiceScreen
import com.mirae.chageunchageun.feature.warning.WarningScreen
import com.mirae.chageunchageun.ui.theme.AppTheme

/**
 * 앱의 화면 목록. PRD 구현 범위(11월 10일 결정)를 따릅니다. 새 화면은 여기에 한 줄 추가합니다(PR 리뷰).
 * - 필수: A3, B1, C1 / 시간이 되면: A5, B2 / SDK 승인 후: E2
 */
enum class AppScreen(val label: String, val debugOnly: Boolean = false) {
    HOME("홈"),
    ONBOARDING("첫 실행"),
    MIRROR("미러 맞추기"),
    PARKING("B1 주차"),
    WARNING("C1 경고"),
    PRACTICE("조작 연습"),
    TOLL("B2 톨게이트"),
    VOICE("E2 음성"),
    SIGNALS("신호 점검", debugOnly = true),
    DEBUG("디버그", debugOnly = true),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as ChageunApp).graph
        setContent { AppTheme { AppRoot(graph) } }
    }
}

@Composable
fun AppRoot(graph: AppGraph) {
    var current by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    val screens = AppScreen.entries.filter { !it.debugOnly || BuildConfig.DEBUG }
    LaunchedEffect(graph) { graph.screenRequests.collect { current = it } }

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
                    AppScreen.MIRROR -> MirrorScreen(graph, onDone = { current = AppScreen.HOME })
                    AppScreen.PARKING -> ParkingScreen(graph)
                    AppScreen.WARNING -> WarningScreen(graph)
                    AppScreen.PRACTICE -> PracticeScreen(graph)
                    AppScreen.TOLL -> TollScreen(graph)
                    AppScreen.VOICE -> VoiceScreen(graph)
                    AppScreen.SIGNALS -> SignalCheckScreen(graph)
                    AppScreen.DEBUG -> DebugPanel(graph)
                }
            }
        }
    }
}
