package com.mirae.chageunchageun.feature.warning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.port.WarningSource
import com.mirae.chageunchageun.domain.guidance.WarningGuidance
import com.mirae.chageunchageun.domain.guidance.WarningGuide
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 화면이 그릴 상태. [available]이 false면 경고 상태를 모르는 것입니다. */
data class WarningUi(
    val available: Boolean = false,
    val guides: List<WarningGuide> = emptyList(),
    val source: Source? = null,
)

class WarningViewModel(warnings: WarningSource, announcer: WarningAnnouncer) : ViewModel() {

    val ui: StateFlow<WarningUi> = warnings.activeWarnings
        .map { signal ->
            when (signal) {
                is Signal.Value -> WarningUi(true, WarningGuidance.ordered(signal.value), signal.source)
                is Signal.Unavailable -> WarningUi()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WarningUi())

    init {
        // 임시: runtime/AppGraph 변경 리뷰가 끝나면 앱 시작 시점으로 옮깁니다. 그때까지 C1 탭을 한 번 열어야 합니다.
        viewModelScope.launch { announcer.run() }
    }
}
