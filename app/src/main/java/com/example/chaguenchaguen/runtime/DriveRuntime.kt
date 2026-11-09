package com.example.chaguenchaguen.runtime

import com.example.chaguenchaguen.core.model.DrivePhase
import com.example.chaguenchaguen.core.model.NOT_YET
import com.example.chaguenchaguen.core.model.SituationEvent
import com.example.chaguenchaguen.core.port.DrivingSignals
import com.example.chaguenchaguen.domain.drive.DrivePhaseResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 앱 수명 동안 하나만 도는 주행 런타임.
 * - [phase], [canShowDetail]: 모든 화면이 같은 기준으로 주행 제한을 적용하도록 공유합니다.
 * - [events]: 감지기(domain)가 [report]한 상황. 안내 정책(재균)과 기록(수현)이 구독합니다.
 */
class DriveRuntime(signals: DrivingSignals, scope: CoroutineScope) {

    val phase: StateFlow<DrivePhase> = signals.gear
        .map(DrivePhaseResolver::phase)
        .stateIn(scope, SharingStarted.Eagerly, DrivePhaseResolver.phase(NOT_YET))

    val canShowDetail: StateFlow<Boolean> = combine(signals.gear, signals.uxRestricted) { gear, ux ->
        DrivePhaseResolver.canShowDetail(gear, ux)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _events = MutableSharedFlow<SituationEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<SituationEvent> = _events.asSharedFlow()

    fun report(event: SituationEvent) {
        _events.tryEmit(event)
    }
}
