package com.mirae.chageunchageun.platform.vehicle

import ai.pleos.playground.vehicle.api.model.telltale.TelltaleData
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.core.model.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PleosWarningSourceTest {

    /** SDK 흉내: 조회 콜백과 리스너를 테스트가 직접 부릅니다. */
    private class FakeApi : TelltaleApi {
        var onResult: ((Telltales) -> Unit)? = null
        var onFailed: ((Exception) -> Unit)? = null
        var listener: TelltaleInfoListener? = null
        var unregistered: TelltaleInfoListener? = null

        override fun get(onResult: (Telltales) -> Unit, onFailed: (Exception) -> Unit) {
            this.onResult = onResult
            this.onFailed = onFailed
        }

        override fun register(listener: TelltaleInfoListener) {
            this.listener = listener
        }

        override fun unregister(listener: TelltaleInfoListener) {
            unregistered = listener
        }
    }

    // color=4, priority=-1, warningCount=0 is what the emulator sends.
    private fun telltales(vararg items: Pair<TelltaleType, TelltaleState>) =
        Telltales(items.map { (type, state) -> TelltaleData(type, state, 4, -1, 0) })

    private fun on(vararg types: TelltaleType) = telltales(*types.map { it to TelltaleState.ON }.toTypedArray())

    @Test
    fun `정해진 경고등만 경고로 옮긴다`() {
        val expected = mapOf(
            TelltaleType.TPMS to WarningType.TIRE_PRESSURE,
            TelltaleType.PARKING_BRAKE to WarningType.PARKING_BRAKE,
            TelltaleType.ELECTRONIC_PARKING_BRAKE to WarningType.PARKING_BRAKE,
            TelltaleType.MDPS to WarningType.STEERING_ASSIST,
            TelltaleType.ABS to WarningType.ABS,
            TelltaleType.ESC to WarningType.ESC,
            TelltaleType.ESC_OFF to WarningType.ESC_OFF,
            TelltaleType.EV_SYSTEM to WarningType.EV_SYSTEM,
            TelltaleType.POWER_DOWN to WarningType.POWER_DOWN,
            TelltaleType.LOW_VOLT_BATTERY to WarningType.LOW_VOLT_BATTERY,
            TelltaleType.AIRBAG to WarningType.AIRBAG,
        )
        TelltaleType.entries.forEach { type ->
            assertEquals(type.name, setOfNotNull(expected[type]), warningsFrom(on(type)))
        }
    }

    @Test
    fun `켜짐과 깜빡임은 켜진 것이고 꺼짐과 목록에 없음은 꺼진 것이다`() {
        assertEquals(setOf(WarningType.TIRE_PRESSURE), warningsFrom(telltales(TelltaleType.TPMS to TelltaleState.ON)))
        assertEquals(setOf(WarningType.TIRE_PRESSURE), warningsFrom(telltales(TelltaleType.TPMS to TelltaleState.BLINK)))
        assertEquals(emptySet<WarningType>(), warningsFrom(telltales(TelltaleType.TPMS to TelltaleState.OFF)))
        assertEquals(emptySet<WarningType>(), warningsFrom(telltales()))
    }

    @Test
    fun `두 개가 함께 켜지면 둘 다 나오고 옮기지 않는 경고등은 빠진다`() {
        assertEquals(
            setOf(WarningType.TIRE_PRESSURE, WarningType.ABS),
            warningsFrom(on(TelltaleType.TPMS, TelltaleType.SEAT_BELT, TelltaleType.ABS, TelltaleType.ENGINE)),
        )
    }

    @Test
    fun `조회와 구독 값을 PLEOS 출처로 내보내고 수집이 끝나면 같은 리스너로 해제한다`() = runTest(UnconfinedTestDispatcher()) {
        val api = FakeApi()
        val seen = mutableListOf<Signal<Set<WarningType>>>()
        val job = launch { PleosWarningSource(api).activeWarnings.collect { seen += it } }

        api.onResult!!(telltales())
        api.listener!!.onTelltaleInfoUpdated(on(TelltaleType.TPMS))
        api.listener!!.onTelltaleInfoUpdated(telltales())

        assertTrue(seen.first() is Signal.Unavailable) // not received yet
        assertEquals(
            listOf(emptySet(), setOf(WarningType.TIRE_PRESSURE), emptySet()),
            seen.drop(1).map { it.valueOrNull() },
        )
        assertTrue(seen.drop(1).all { (it as Signal.Value).source == Source.PLEOS })

        assertNull(api.unregistered)
        job.cancelAndJoin()
        assertSame(api.listener, api.unregistered)
    }

    @Test
    fun `조회나 구독이 실패하면 빈 집합이 아니라 사유를 내보낸다`() = runTest(UnconfinedTestDispatcher()) {
        val api = FakeApi()
        val seen = mutableListOf<Signal<Set<WarningType>>>()
        val job = launch { PleosWarningSource(api).activeWarnings.collect { seen += it } }

        api.onFailed!!(SecurityException("no permission"))
        api.listener!!.onFailed(IllegalStateException("service gone"))

        assertEquals(
            listOf("getTelltalesInfo failed: no permission", "registerTelltales failed: service gone"),
            seen.drop(1).map { (it as Signal.Unavailable).reason },
        )
        job.cancelAndJoin()
    }
}
