package com.mirae.chageunchageun.feature.warning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirae.chageunchageun.core.model.DrivePhase
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.SpeechPriority
import com.mirae.chageunchageun.core.port.SpeechOutput
import com.mirae.chageunchageun.core.port.WarningSource
import com.mirae.chageunchageun.domain.guidance.WarningGuidance
import com.mirae.chageunchageun.domain.guidance.WarningGuide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    /** "다시 듣기"에 씁니다. 주지 않으면 다시 듣기가 동작하지 않습니다. */
    private val speech: SpeechOutput? = null,
) : ViewModel() {

    private var replay: Job? = null

    /** 경고 문장을 한 번 다시 말합니다. 말하는 중에 또 누르면 무시합니다. 새 경고(WARNING)가 오면 끊깁니다. */
    fun listenAgain(guide: WarningGuide) {
        val text = WarningGuidance.speech(guide) ?: return
        if (speech == null || replay?.isActive == true) return
        replay = viewModelScope.launch {
            try {
                speech.speak(text, SpeechPriority.NORMAL)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 음성이 실패해도 화면은 그대로 둡니다.
            }
        }
    }

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
