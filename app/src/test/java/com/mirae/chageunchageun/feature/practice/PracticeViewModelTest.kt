package com.mirae.chageunchageun.feature.practice

import com.mirae.chageunchageun.MainDispatcherRule
import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.WiperState
import com.mirae.chageunchageun.core.port.SteeringSource
import com.mirae.chageunchageun.demo.FakeControlsSource
import com.mirae.chageunchageun.demo.FakeSteeringSource
import com.mirae.chageunchageun.demo.FakeVehicleBus
import com.mirae.chageunchageun.domain.practice.PracticeItem
import com.mirae.chageunchageun.domain.practice.PracticeReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PracticeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val bus = FakeVehicleBus()
    private fun viewModel(steering: SteeringSource = FakeSteeringSource(bus)) =
        PracticeViewModel(FakeControlsSource(bus), steering)

    @Test
    fun `가짜 차량에서 상향등을 켰다 끄면 완료로 기록된다`() {
        val vm = viewModel()
        vm.open(PracticeItem.HIGH_BEAM)
        assertEquals(PracticeReading.Switch(on = false), vm.ui.value.reading)
        assertEquals(Source.SCRIPTED, vm.ui.value.source)

        bus.setLamp(Lamp.HIGH_BEAM, LampState.ON)
        assertEquals(1, vm.ui.value.session?.index)
        bus.setLamp(Lamp.HIGH_BEAM, LampState.OFF)

        assertTrue(vm.ui.value.session!!.isDone)
        assertEquals(setOf(PracticeItem.HIGH_BEAM), vm.ui.value.completed)
        assertEquals(PracticeItem.LOW_BEAM, vm.ui.value.nextItem)
    }

    @Test
    fun `다른 조작부의 신호로는 넘어가지 않는다`() {
        val vm = viewModel()
        vm.open(PracticeItem.WIPER)
        bus.setLamp(Lamp.HIGH_BEAM, LampState.ON)
        bus.brakePressed.value = true
        assertEquals(0, vm.ui.value.session?.index)

        bus.frontWiper.value = WiperState.ON
        assertEquals(1, vm.ui.value.session?.index)
    }

    @Test
    fun `값을 못 받는 조작부는 직접 확인으로만 넘어간다`() {
        val unavailable = object : SteeringSource {
            override val angleDeg: Flow<Signal<Float>> = flowOf(Signal.Unavailable("adapter not implemented"))
        }
        val vm = viewModel(unavailable)
        vm.open(PracticeItem.STEERING)
        assertEquals(PracticeReading.None, vm.ui.value.reading)
        assertNull(vm.ui.value.source)
        assertEquals(0, vm.ui.value.session?.index)

        repeat(3) { vm.confirmManually() }
        assertTrue(vm.ui.value.session!!.isDone)
    }

    @Test
    fun `목록으로 돌아가면 신호를 더 보지 않고 완료 기록은 남는다`() {
        val vm = viewModel()
        vm.open(PracticeItem.BRAKE)
        bus.brakePressed.value = true
        bus.brakePressed.value = false
        vm.backToList()
        assertNull(vm.ui.value.session)
        assertEquals(setOf(PracticeItem.BRAKE), vm.ui.value.completed)

        vm.resetAll()
        assertFalse(PracticeItem.BRAKE in vm.ui.value.completed)
    }

    @Test
    fun `주간주행등과 와이퍼 기타 상태는 켜짐으로 보지 않는다`() {
        assertEquals(
            PracticeReading.Switch(on = false),
            lampReading(Signal.Value(LampState.DAYTIME_RUNNING, Source.PLEOS)),
        )
        assertEquals(PracticeReading.None, wiperReading(Signal.Value(WiperState.OTHER, Source.PLEOS)))
    }
}
