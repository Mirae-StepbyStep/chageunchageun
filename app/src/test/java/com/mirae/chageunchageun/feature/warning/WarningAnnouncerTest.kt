package com.mirae.chageunchageun.feature.warning

import com.mirae.chageunchageun.MainDispatcherRule
import com.mirae.chageunchageun.core.model.Severity
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.SituationEvent
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.SpeechPriority
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.core.port.SpeechOutput
import com.mirae.chageunchageun.core.port.WarningSource
import com.mirae.chageunchageun.demo.FakeDriveStateSource
import com.mirae.chageunchageun.demo.FakeSpeechOutput
import com.mirae.chageunchageun.demo.FakeVehicleBus
import com.mirae.chageunchageun.demo.FakeWarningSource
import com.mirae.chageunchageun.demo.ScenarioParser
import com.mirae.chageunchageun.demo.ScenarioPlayer
import com.mirae.chageunchageun.domain.guidance.WarningGuidance
import com.mirae.chageunchageun.runtime.DriveRuntime
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WarningAnnouncerTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val warnings = MutableStateFlow<Signal<Set<WarningType>>>(value())
    private val source = object : WarningSource {
        override val activeWarnings = warnings
    }
    private val speech = RecordingSpeech()
    private val events = mutableListOf<SituationEvent>()

    private fun value(vararg types: WarningType): Signal<Set<WarningType>> =
        Signal.Value(types.toSet(), Source.SCRIPTED)

    private class RecordingSpeech : SpeechOutput {
        val spoken = mutableListOf<Pair<String, SpeechPriority>>()
        var failNext = false

        override suspend fun speak(text: String, priority: SpeechPriority) {
            if (failNext) {
                failNext = false
                throw IllegalStateException("TTS 실패")
            }
            spoken += text to priority
        }

        override fun stop() = Unit
    }

    private fun TestScope.start() {
        val runtime = DriveRuntime(FakeDriveStateSource(FakeVehicleBus()), backgroundScope)
        // events 는 재생(replay)이 없으므로 알리기 전에 구독합니다.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { runtime.events.toList(events) }
        backgroundScope.launch { WarningAnnouncer(source, speech, runtime).run() }
        runCurrent()
    }

    private fun TestScope.emit(signal: Signal<Set<WarningType>>) {
        warnings.value = signal
        runCurrent()
    }

    private fun sentence(type: WarningType) = WarningGuidance.speech(WarningGuidance.guide(type))!!

    @Test
    fun `새 경고는 경고 우선순위 음성 한 번과 이벤트 하나를 만든다`() = runTest {
        start()
        emit(value(WarningType.TIRE_PRESSURE))

        assertEquals(listOf(sentence(WarningType.TIRE_PRESSURE) to SpeechPriority.WARNING), speech.spoken)
        val event = events.single()
        assertEquals("warning.tire_pressure", event.id)
        assertEquals(Severity.CAUTION, event.severity)
        assertEquals("SCRIPTED", event.facts["source"])
    }

    @Test
    fun `경고가 추가되면 새 경고만 말한다`() = runTest {
        start()
        emit(value(WarningType.TIRE_PRESSURE))
        emit(value(WarningType.TIRE_PRESSURE, WarningType.BRAKE_FLUID))

        assertEquals(
            listOf(sentence(WarningType.TIRE_PRESSURE), sentence(WarningType.BRAKE_FLUID)),
            speech.spoken.map { it.first },
        )
    }

    @Test
    fun `값을 잠시 못 받아도 해제로 보지 않는다`() = runTest {
        start()
        emit(Signal.Unavailable("test"))
        assertTrue(speech.spoken.isEmpty())
        assertTrue(events.isEmpty())

        emit(value(WarningType.TIRE_PRESSURE))
        emit(Signal.Unavailable("test"))
        emit(value(WarningType.TIRE_PRESSURE))
        assertEquals(1, speech.spoken.size)
    }

    @Test
    fun `해제 후 다시 켜지면 같은 문장을 줄이지 않고 말한다`() = runTest {
        start()
        emit(value(WarningType.TIRE_PRESSURE))
        emit(value())
        emit(value(WarningType.TIRE_PRESSURE))

        assertEquals(
            List(2) { sentence(WarningType.TIRE_PRESSURE) },
            speech.spoken.map { it.first },
        )
    }

    @Test
    fun `정보 경고는 이벤트만 남기고 말하지 않는다`() = runTest {
        start()
        emit(value(WarningType.WASHER_FLUID))

        assertTrue(speech.spoken.isEmpty())
        assertEquals(Severity.INFO, events.single().severity)
    }

    @Test
    fun `동시에 켜지면 위험부터 말한다`() = runTest {
        start()
        emit(value(WarningType.TIRE_PRESSURE, WarningType.BRAKE_FLUID))

        assertEquals(
            listOf(sentence(WarningType.BRAKE_FLUID), sentence(WarningType.TIRE_PRESSURE)),
            speech.spoken.map { it.first },
        )
    }

    @Test
    fun `c1 시나리오는 공기압과 브레이크액만 말한다`() = runTest {
        val bus = FakeVehicleBus()
        val runtime = DriveRuntime(FakeDriveStateSource(bus), backgroundScope)
        backgroundScope.launch {
            WarningAnnouncer(FakeWarningSource(bus), FakeSpeechOutput(bus), runtime).run()
        }
        runCurrent()

        val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/c1_warning.json").readText())
        launch { ScenarioPlayer(bus).play(scenario) }
        advanceUntilIdle()

        assertEquals(
            listOf(sentence(WarningType.TIRE_PRESSURE), sentence(WarningType.BRAKE_FLUID)),
            bus.spoken.value,
        )
    }

    @Test
    fun `음성이 실패해도 이벤트는 남고 다음 경고를 말한다`() = runTest {
        start()
        speech.failNext = true
        emit(value(WarningType.TIRE_PRESSURE))
        assertEquals("warning.tire_pressure", events.single().id)

        emit(value(WarningType.TIRE_PRESSURE, WarningType.BRAKE_FLUID))
        assertEquals(listOf(sentence(WarningType.BRAKE_FLUID)), speech.spoken.map { it.first })
    }
}
