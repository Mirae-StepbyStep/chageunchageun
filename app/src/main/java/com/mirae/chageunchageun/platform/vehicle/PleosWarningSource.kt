package com.mirae.chageunchageun.platform.vehicle

import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.core.port.WarningSource
import com.mirae.chageunchageun.platform.NOT_IMPLEMENTED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Pleos Vehicle SDK의 Safety 경고(Telltale 등) 구독. 담당: 재균
 * 생성자는 AppGraph에 이미 연결돼 있으니 바꾸지 말고 본문만 채웁니다.
 */
class PleosWarningSource(private val session: PleosVehicleSession) : WarningSource {
    override val activeWarnings: Flow<Signal<Set<WarningType>>> = flowOf(NOT_IMPLEMENTED)
}
