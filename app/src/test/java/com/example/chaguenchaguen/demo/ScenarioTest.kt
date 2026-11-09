package com.example.chaguenchaguen.demo

import com.example.chaguenchaguen.core.model.DrivePhase
import com.example.chaguenchaguen.core.model.Gear
import com.example.chaguenchaguen.core.model.TurnSignal
import com.example.chaguenchaguen.core.model.WarningType
import com.example.chaguenchaguen.feature.home.HomeViewModel
import com.example.chaguenchaguen.runtime.DriveRuntime
import java.io.File
import com.example.chaguenchaguen.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScenarioTest {

    @get:Rule
    val main = MainDispatcherRule()

    /** assets 의 모든 시나리오가 파싱되는지 확인합니다. 시나리오를 추가하면 자동으로 검사됩니다. */
    @Test
    fun `모든 시나리오 파일이 파싱된다`() {
        val dir = File("src/main/assets/scenarios")
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue("시나리오 파일이 없습니다: ${dir.absolutePath}", files.isNotEmpty())
        files.forEach { f ->
            val s = ScenarioParser.parse(f.readText())
            assertTrue("${f.name}: 단계가 없습니다", s.steps.isNotEmpty())
        }
    }

    @Test
    fun `시나리오를 재생하면 시간에 맞춰 가짜 차량 값이 바뀐다`() = runTest {
        val bus = FakeVehicleBus()
        val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/basic_drive.json").readText())
        val job = launch { ScenarioPlayer(bus).play(scenario) }

        advanceTimeBy(2_500)
        assertEquals(Gear.D, bus.gear.value)
        advanceTimeBy(4_000) // 6.5초
        assertEquals(TurnSignal.LEFT, bus.turnSignal.value)
        advanceTimeBy(7_000) // 13.5초
        assertEquals(setOf(WarningType.TIRE_PRESSURE), bus.warnings.value)
        advanceUntilIdle()
        assertEquals(Gear.P, bus.gear.value)
        job.join()
    }

    @Test
    fun `예시 기능 - 가짜 포트로 홈 상태가 만들어진다`() = runTest {
        val bus = FakeVehicleBus()
        val signals = FakeDrivingSignals(bus)
        val runtime = DriveRuntime(signals, backgroundScope)
        val vm = HomeViewModel(signals, runtime)

        bus.gear.value = Gear.D
        bus.turnSignal.value = TurnSignal.RIGHT
        val ui = vm.ui.first { it.turnSignal == TurnSignal.RIGHT && it.gear == Gear.D }
        assertEquals(DrivePhase.DRIVING, ui.phase)
    }
}
