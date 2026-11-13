package com.mirae.chageunchageun.runtime

import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.sourceOrNull
import com.mirae.chageunchageun.core.model.valueOrNull
import com.mirae.chageunchageun.core.port.DriveStateSource
import com.mirae.chageunchageun.demo.FakeDriveStateSource
import com.mirae.chageunchageun.demo.FakeVehicleBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortSwitchTest {

    private class RealStub : DriveStateSource {
        val gearValue = MutableStateFlow<Signal<Gear>>(Signal.Unavailable("adapter not implemented"))
        override val gear: Flow<Signal<Gear>> = gearValue
        override val uxRestricted: Flow<Signal<Boolean>> = flowOf(Signal.Unavailable("adapter not implemented"))
    }

    @Test
    fun `기본은 가짜이고 모드를 바꾸면 구독 중인 Flow가 넘어간다`() = runTest(UnconfinedTestDispatcher()) {
        val bus = FakeVehicleBus()
        val real = RealStub()
        val modes = PortModes()
        val port = SwitchingDriveStateSource(PortSwitch(modes, PortId.DRIVE_STATE, real, FakeDriveStateSource(bus)))

        val seen = mutableListOf<Signal<Gear>>()
        backgroundScope.launch { port.gear.collect { seen += it } }

        assertEquals(Source.SCRIPTED, seen.last().sourceOrNull())
        assertEquals(Gear.P, seen.last().valueOrNull())

        // 실제로 바꾸면 값을 못 받는 상태가 그대로 보입니다(가짜로 자동 대체하지 않음).
        modes.set(PortId.DRIVE_STATE, PortMode.REAL)
        assertTrue(seen.last() is Signal.Unavailable)

        real.gearValue.value = Signal.Value(Gear.D, Source.AAOS)
        assertEquals(Source.AAOS, seen.last().sourceOrNull())

        // 실제 모드에서는 가짜 차량을 바꿔도 영향이 없습니다.
        bus.gear.value = Gear.R
        assertEquals(Gear.D, seen.last().valueOrNull())

        modes.set(PortId.DRIVE_STATE, PortMode.FAKE)
        assertEquals(Gear.R, seen.last().valueOrNull())
        assertEquals(Source.SCRIPTED, seen.last().sourceOrNull())
    }

    @Test
    fun `한 포트를 바꿔도 다른 포트의 모드는 그대로다`() {
        val modes = PortModes()
        modes.set(PortId.WARNING, PortMode.REAL)
        assertEquals(PortMode.REAL, modes.get(PortId.WARNING))
        assertEquals(PortMode.FAKE, modes.get(PortId.STEERING))
        modes.setAll(PortMode.FAKE)
        assertEquals(PortMode.FAKE, modes.get(PortId.WARNING))
    }

    @Test
    fun `구현된 포트만 실제로 시작하고 나머지는 가짜로 시작한다`() {
        val modes = PortModes(real = CONNECTED_PORTS)
        assertEquals(PortMode.REAL, modes.get(PortId.CONTROLS))
        assertEquals(PortMode.REAL, modes.get(PortId.DRIVE_STATE))
        assertEquals(PortMode.REAL, modes.get(PortId.STEERING))
        assertEquals(PortMode.FAKE, modes.get(PortId.LOCATION))
        assertEquals(PortMode.FAKE, PortModes().get(PortId.CONTROLS))
    }
}
