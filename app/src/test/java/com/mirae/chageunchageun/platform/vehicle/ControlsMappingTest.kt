package com.mirae.chageunchageun.platform.vehicle

import ai.pleos.playground.vehicle.constant.control.LightState
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.WiperState
import org.junit.Assert.assertEquals
import org.junit.Test
import ai.pleos.playground.vehicle.constant.control.WiperState as PleosWiperState

class ControlsMappingTest {

    @Test
    fun `Pleos 등화 상태를 앱 상태로 바꾼다`() {
        assertEquals(LampState.OFF, lampStateFromPleos(LightState.OFF))
        assertEquals(LampState.ON, lampStateFromPleos(LightState.ON))
        assertEquals(LampState.DAYTIME_RUNNING, lampStateFromPleos(LightState.DAYTIME_RUNNING))
    }

    @Test
    fun `Pleos 와이퍼 상태를 앱 상태로 바꾸고 정비 모드는 OTHER`() {
        assertEquals(WiperState.OFF, wiperStateFromPleos(PleosWiperState.OFF))
        assertEquals(WiperState.ON, wiperStateFromPleos(PleosWiperState.ON))
        assertEquals(WiperState.OTHER, wiperStateFromPleos(PleosWiperState.OTHER))
        assertEquals(WiperState.OTHER, wiperStateFromPleos(PleosWiperState.SERVICE))
    }

    @Test
    fun `조향각은 SDK 값의 부호를 뒤집어 콘솔과 맞춘다`() {
        assertEquals(-161f, angleFromSdk(161f))
        assertEquals(90f, angleFromSdk(-90f))
        assertEquals(0f, angleFromSdk(0f))
        assertEquals(null, angleFromSdk(null))
    }
}
