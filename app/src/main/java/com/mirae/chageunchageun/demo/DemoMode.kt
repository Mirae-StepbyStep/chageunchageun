package com.mirae.chageunchageun.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mirae.chageunchageun.BuildConfig

/**
 * 시연 모드. 켜면 홈에 시연 도구(신호 점검·디버그)와 첫 실행 초기화, 주차 화면에 "가짜 차량으로 전환"이 보입니다.
 * debug 빌드는 켜진 채 시작하고, release 빌드는 홈의 차근차근 로고를 길게 눌러 켭니다. 앱을 다시 켜면 꺼집니다.
 */
object DemoMode {
    var enabled by mutableStateOf(BuildConfig.DEBUG)
}
