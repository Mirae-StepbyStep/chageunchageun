package com.mirae.chageunchageun.core.port

import com.mirae.chageunchageun.core.model.GeoPoint
import com.mirae.chageunchageun.core.model.Signal
import kotlinx.coroutines.flow.Flow

/** B1 위치와 도착. 담당: 민서 */
interface LocationSource {
    val location: Flow<Signal<GeoPoint>>

    /** 목적지 도착 이벤트. 값은 도착지 이름(모르면 빈 문자열). */
    val arrivals: Flow<String>
}
