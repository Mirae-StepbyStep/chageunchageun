package com.mirae.chageunchageun.platform

import com.mirae.chageunchageun.core.model.NOT_YET
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignalFlowTest {

    /** SDK 흉내: 조회 콜백과 리스너를 테스트가 직접 부릅니다. */
    private class FakeSdk {
        var pendingGet: SignalSink<Int>? = null
        var listener: SignalSink<Int>? = null
        var unregistered = false
    }

    private fun flowOf(sdk: FakeSdk) = signalFlow<Int>(
        source = Source.PLEOS,
        getCurrent = { sdk.pendingGet = it },
        register = { sink ->
            sdk.listener = sink
            return@signalFlow { sdk.unregistered = true }
        },
    )

    private fun value(v: Int) = Signal.Value(v, Source.PLEOS)

    private fun Signal<Int>.ignoringTime() = if (this is Signal.Value) copy(atMillis = 0) else this

    @Test
    fun `조회 결과와 리스너 값이 차례로 나오고 수집이 끝나면 해제한다`() = runTest(UnconfinedTestDispatcher()) {
        val sdk = FakeSdk()
        val seen = mutableListOf<Signal<Int>>()
        val job = launch { flowOf(sdk).collect { seen += it.ignoringTime() } }

        assertEquals(listOf<Signal<Int>>(NOT_YET), seen)
        sdk.pendingGet!!.value(1)
        sdk.listener!!.value(2)
        assertEquals(listOf(NOT_YET, value(1).ignoringTime(), value(2).ignoringTime()), seen)

        assertFalse(sdk.unregistered)
        job.cancelAndJoin()
        assertTrue(sdk.unregistered)
    }

    @Test
    fun `리스너가 먼저 값을 주면 늦게 온 조회 결과는 버린다`() = runTest(UnconfinedTestDispatcher()) {
        val sdk = FakeSdk()
        val seen = mutableListOf<Signal<Int>>()
        val job = launch { flowOf(sdk).collect { seen += it.ignoringTime() } }

        sdk.listener!!.value(5)
        sdk.pendingGet!!.value(1)
        sdk.pendingGet!!.unavailable("late failure")
        assertEquals(value(5).ignoringTime(), seen.last())
        job.cancelAndJoin()
    }

    @Test
    fun `실패와 null은 Unavailable로 나온다`() = runTest(UnconfinedTestDispatcher()) {
        val sdk = FakeSdk()
        val seen = mutableListOf<Signal<Int>>()
        val job = launch { flowOf(sdk).collect { seen += it } }

        sdk.pendingGet!!.unavailable("permission denied")
        assertEquals(Signal.Unavailable("permission denied"), seen.last())
        sdk.listener!!.value(null)
        assertEquals(Signal.Unavailable("null value"), seen.last())
        job.cancelAndJoin()
    }

    @Test
    fun `등록이나 조회가 예외를 던지면 사유를 담은 Unavailable이 나온다`() = runTest(UnconfinedTestDispatcher()) {
        val registerFails = signalFlow<Int>(
            source = Source.AAOS,
            getCurrent = { error("should not be called") },
            register = { throw IllegalStateException("Car service not available") },
        )
        val seen = mutableListOf<Signal<Int>>()
        val job = launch { registerFails.collect { seen += it } }
        assertEquals(Signal.Unavailable("register failed: Car service not available"), seen.last())
        job.cancelAndJoin()

        var unregistered = false
        val getFails = signalFlow<Int>(
            source = Source.AAOS,
            getCurrent = { throw SecurityException("no permission") },
            register = { return@signalFlow { unregistered = true } },
        )
        val seen2 = mutableListOf<Signal<Int>>()
        val job2 = launch { getFails.collect { seen2 += it } }
        assertEquals(Signal.Unavailable("get failed: no permission"), seen2.last())
        job2.cancelAndJoin()
        assertTrue(unregistered)
    }
}
