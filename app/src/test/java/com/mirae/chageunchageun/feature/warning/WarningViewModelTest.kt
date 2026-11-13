package com.mirae.chageunchageun.feature.warning

import com.mirae.chageunchageun.MainDispatcherRule
import com.mirae.chageunchageun.core.model.DrivePhase
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.SpeechPriority
import com.mirae.chageunchageun.core.port.SpeechOutput
import com.mirae.chageunchageun.domain.guidance.WarningGuidance
import kotlinx.coroutines.CompletableDeferred
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.core.port.WarningSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WarningViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val warnings = MutableStateFlow<Signal<Set<WarningType>>>(Signal.Unavailable("test"))
    private val source = object : WarningSource {
        override val activeWarnings = warnings
    }

    private fun viewModel() = WarningViewModel(source)

    @Test
    fun `위험 경고가 먼저 오고 출처를 함께 준다`() = runTest {
        val vm = viewModel()
        warnings.value = Signal.Value(setOf(WarningType.TIRE_PRESSURE, WarningType.BRAKE_FLUID), Source.SCRIPTED)

        val ui = vm.ui.first { it.guides.isNotEmpty() }
        assertEquals(listOf(WarningType.BRAKE_FLUID, WarningType.TIRE_PRESSURE), ui.guides.map { it.type })
        assertEquals(Source.SCRIPTED, ui.source)
    }

    @Test
    fun `경고가 없음은 값을 받은 상태다`() = runTest {
        val vm = viewModel()
        warnings.value = Signal.Value(emptySet(), Source.SCRIPTED)

        val ui = vm.ui.first { it.available }
        assertTrue(ui.guides.isEmpty())
    }

    @Test
    fun `값을 못 받으면 사용 불가로 둔다`() = runTest {
        val vm = viewModel()

        val ui = vm.ui.first()
        assertFalse(ui.available)
        assertTrue(ui.guides.isEmpty())
    }

    @Test
    fun `주차 브레이크 경고는 주차 중에는 화면에서 뺀다`() = runTest {
        val phase = MutableStateFlow(DrivePhase.PARKED)
        val vm = WarningViewModel(source, phase)
        warnings.value = Signal.Value(setOf(WarningType.PARKING_BRAKE, WarningType.TIRE_PRESSURE), Source.SCRIPTED)

        assertEquals(listOf(WarningType.TIRE_PRESSURE), vm.ui.first { it.guides.isNotEmpty() }.guides.map { it.type })

        phase.value = DrivePhase.DRIVING
        assertEquals(
            listOf(WarningType.TIRE_PRESSURE, WarningType.PARKING_BRAKE),
            vm.ui.first { it.guides.size == 2 }.guides.map { it.type },
        )
    }

    private class SlowSpeech : SpeechOutput {
        val spoken = mutableListOf<Pair<String, SpeechPriority>>()
        val done = CompletableDeferred<Unit>()

        override suspend fun speak(text: String, priority: SpeechPriority) {
            spoken += text to priority
            done.await()
        }

        override fun stop() = Unit
    }

    @Test
    fun `다시 듣기는 경고 문장을 한 번 말하고 말하는 중에는 또 말하지 않는다`() = runTest {
        val speech = SlowSpeech()
        val vm = WarningViewModel(source, speech = speech)
        val guide = WarningGuidance.guide(WarningType.TIRE_PRESSURE)

        vm.listenAgain(guide)
        vm.listenAgain(guide)
        assertEquals(listOf(WarningGuidance.speech(guide)!! to SpeechPriority.NORMAL), speech.spoken)

        speech.done.complete(Unit)
        vm.listenAgain(guide)
        assertEquals(2, speech.spoken.size)
    }

    @Test
    fun `정보 경고는 다시 들을 문장이 없다`() = runTest {
        val speech = SlowSpeech()
        WarningViewModel(source, speech = speech).listenAgain(WarningGuidance.guide(WarningType.ESC_OFF))

        assertTrue(speech.spoken.isEmpty())
    }
}
