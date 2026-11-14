package com.mirae.chageunchageun.runtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirae.chageunchageun.R
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
import com.mirae.chageunchageun.ui.common.CarScale
import com.mirae.chageunchageun.ui.theme.AppTheme

/**
 * 앱의 화면 목록. PRD 구현 범위(11월 10일 결정)를 따릅니다. 새 화면은 여기에 한 줄 추가합니다(PR 리뷰).
 * - 필수: A3, B1, C1 / 시간이 되면: A5, B2 / SDK 승인 후: E2
 */
enum class AppScreen(val label: String, val debugOnly: Boolean = false, val hidden: Boolean = false) {
    HOME("홈"),
    ONBOARDING("내 설정"), // 첫 실행을 마치기 전에는 상단 제목이 "첫 실행"으로 보입니다
    MIRROR("미러 맞추기"),
    PARKING("스마트 주차 코치"),
    WARNING("경고등 안내"),
    PRACTICE("조작 연습"),
    // 시연 영상에서 쓰지 않아 숨깁니다(화면 전환 요청도 받지 않음). 다시 쓰려면 hidden = true를 지우고 홈에 입구를 답니다.
    TOLL("B2 톨게이트", hidden = true),
    VOICE("E2 음성", hidden = true),
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
    LaunchedEffect(graph) { graph.screenRequests.collect { if (!it.hidden) current = it } }
    // 홈이 허브입니다. 다른 화면에서는 상단 바의 "홈"과 시스템 뒤로 가기가 홈으로 돌아갑니다.
    val goHome = { current = AppScreen.HOME }
    BackHandler(enabled = current != AppScreen.HOME, onBack = goHome)

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (current != AppScreen.HOME) {
                val firstRunDone by graph.userSettings.firstRunCompleted.collectAsStateWithLifecycle()
                val title = if (current == AppScreen.ONBOARDING && !firstRunDone) "첫 실행" else current.label
                CarScale(Modifier.fillMaxWidth()) { HomeBar(title, goHome) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
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

/** 홈이 아닌 화면 위에 놓는 바: "홈"으로 돌아가는 버튼과 화면 제목. */
@Composable
private fun HomeBar(title: String, onHome: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onHome, Modifier.heightIn(min = 56.dp)) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, Modifier.size(28.dp))
            Text("홈", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
