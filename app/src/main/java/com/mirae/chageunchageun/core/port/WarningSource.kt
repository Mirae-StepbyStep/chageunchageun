package com.mirae.chageunchageun.core.port

import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.WarningType
import kotlinx.coroutines.flow.Flow

/** C1 경고. 켜져 있는 경고의 집합을 내보냅니다. 담당: 재균 */
interface WarningSource {
    val activeWarnings: Flow<Signal<Set<WarningType>>>
}
