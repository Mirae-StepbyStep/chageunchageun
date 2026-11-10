package com.mirae.chageunchageun.platform.nav

import android.content.Context
import com.mirae.chageunchageun.core.model.GeoPoint
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.port.LocationSource
import com.mirae.chageunchageun.platform.NOT_IMPLEMENTED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * 위치(FusedLocation)와 도착(NaviHelper 또는 영역 진입). 담당: 민서
 * 생성자는 AppGraph에 이미 연결돼 있으니 바꾸지 말고 본문만 채웁니다.
 */
class PleosLocationSource(private val context: Context) : LocationSource {
    override val location: Flow<Signal<GeoPoint>> = flowOf(NOT_IMPLEMENTED)
    override val arrivals: Flow<String> = emptyFlow()
}
