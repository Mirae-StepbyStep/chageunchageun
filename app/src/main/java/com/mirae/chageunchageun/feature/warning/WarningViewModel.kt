package com.mirae.chageunchageun.feature.warning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirae.chageunchageun.core.model.DrivePhase
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.port.WarningSource
import com.mirae.chageunchageun.domain.guidance.WarningGuidance
import com.mirae.chageunchageun.domain.guidance.WarningGuide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** 화면이 그릴 상태. [available]이 false면 경고 상태를 모르는 것입니다. */
data class WarningUi(
    val available: Boolean = false,
    val guides: List<WarningGuide> = emptyList(),
    val source: Source? = null,
)

class WarningViewModel(
    warnings: WarningSource,
    /** 주차 브레이크 경고를 주차 중에 숨기는 데만 씁니다. 주지 않으면 숨기지 않습니다. */
    phase: Flow<DrivePhase> = flowOf(DrivePhase.DRIVING),
) : ViewModel() {

    val ui: StateFlow<WarningUi> = warnings.activeWarnings
        .combine(phase) { signal, drivePhase ->
            when (signal) {
                is Signal.Value -> WarningUi(
                    true,
                    WarningGuidance.ordered(WarningGuidance.relevant(signal.value, drivePhase)),
                    signal.source,
                )
                is Signal.Unavailable -> WarningUi()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WarningUi())
}
