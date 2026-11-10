package com.mirae.chageunchageun.feature.mirror

import androidx.lifecycle.ViewModel
import com.mirae.chageunchageun.domain.mirror.MirrorGuide
import com.mirae.chageunchageun.domain.mirror.MirrorStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 화면이 그릴 상태. */
data class MirrorUi(val step: MirrorStep = MirrorGuide.steps.first()) {
    val index: Int get() = step.ordinal
    val total: Int get() = MirrorGuide.steps.size
    val canGoBack: Boolean get() = !MirrorGuide.isFirst(step)
    val isLast: Boolean get() = MirrorGuide.isLast(step)
    val animated: Boolean get() = MirrorGuide.isAnimated(step)
}

/**
 * A3 미러 조절 안내. 차량 신호를 쓰지 않아 포트를 받지 않습니다.
 * 주행 시작 시 숨김은 화면의 DrivingGate(DriveRuntime.canShowDetail)가 맡고,
 * 단계는 여기 남아 있어 다시 정차하면 보던 단계에서 이어집니다.
 */
class MirrorViewModel : ViewModel() {

    private val _ui = MutableStateFlow(MirrorUi())
    val ui: StateFlow<MirrorUi> = _ui.asStateFlow()

    fun next() = _ui.update { it.copy(step = MirrorGuide.next(it.step)) }

    fun previous() = _ui.update { it.copy(step = MirrorGuide.previous(it.step)) }

    fun restart() = _ui.update { MirrorUi() }
}
