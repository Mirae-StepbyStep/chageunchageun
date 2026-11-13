package com.mirae.chageunchageun.platform.speech

import com.mirae.chageunchageun.core.model.SpeechPriority
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusedSpeakerTest {

    /** 실제 엔진처럼 stop()하면 말하던 문장의 끝 콜백이 옵니다. */
    private class FakeEngine : SpeechEngine {
        var available = true
        val started = mutableListOf<String>()
        var stops = 0
        var ducks = 0
        private var onEnd: (() -> Unit)? = null
        val speaking get() = onEnd != null

        override suspend fun prepare() = available

        override fun start(text: String, onEnd: () -> Unit) {
            started += text
            this.onEnd = onEnd
        }

        override fun stop() {
            stops++
            finish()
        }

        override fun duck() {
            ducks++
        }

        fun finish() {
            onEnd.also { onEnd = null }?.invoke()
        }
    }

    private class FakeFocus : AudioFocus {
        var grant = true
        var requests = 0
        var abandons = 0
        var listener: ((FocusChange) -> Unit)? = null

        override fun request(onChange: (FocusChange) -> Unit): Boolean {
            requests++
            if (grant) listener = onChange
            return grant
        }

        override fun abandon() {
            abandons++
        }
    }

    private val engine = FakeEngine()
    private val focus = FakeFocus()
    private val speaker = FocusedSpeaker(engine, focus)

    private fun TestScope.say(text: String, priority: SpeechPriority = SpeechPriority.NORMAL): Job =
        launch(UnconfinedTestDispatcher(testScheduler)) { speaker.speak(text, priority) }

    @Test
    fun `포커스를 받고 말한 뒤 끝나면 포커스를 돌려준다`() = runTest {
        val job = say("a")
        assertEquals(listOf("a"), engine.started)
        assertFalse(job.isCompleted)

        engine.finish()
        assertTrue(job.isCompleted)
        assertEquals(1, focus.requests)
        assertEquals(1, focus.abandons)
        assertEquals(0, engine.stops)
    }

    @Test
    fun `포커스를 못 받으면 말하지 않고 돌아온다`() = runTest {
        focus.grant = false
        val job = say("a")

        assertTrue(job.isCompleted)
        assertTrue(engine.started.isEmpty())
        assertEquals(0, focus.abandons)
    }

    @Test
    fun `엔진을 쓸 수 없으면 포커스도 요청하지 않고 돌아온다`() = runTest {
        engine.available = false
        val job = say("a")

        assertTrue(job.isCompleted)
        assertEquals(0, focus.requests)
    }

    @Test
    fun `LOSS면 바로 멈추고 포커스를 돌려준다`() = runTest {
        val job = say("a")
        focus.listener!!(FocusChange.LOSS)

        assertTrue(job.isCompleted)
        assertEquals(1, engine.stops)
        assertEquals(1, focus.abandons)
    }

    @Test
    fun `LOSS_TRANSIENT면 멈췄다가 GAIN에서 처음부터 한 번 다시 말한다`() = runTest {
        val job = say("a")
        focus.listener!!(FocusChange.LOSS_TRANSIENT)
        assertFalse(engine.speaking)
        assertFalse(job.isCompleted)

        focus.listener!!(FocusChange.GAIN)
        assertEquals(listOf("a", "a"), engine.started)

        // 두 번째로 잃으면 다시 말하지 않고 끝냅니다.
        focus.listener!!(FocusChange.LOSS_TRANSIENT)
        assertTrue(job.isCompleted)
        focus.listener!!(FocusChange.GAIN)
        assertEquals(2, engine.started.size)
    }

    @Test
    fun `말하는 중의 GAIN은 아무것도 바꾸지 않는다`() = runTest {
        val job = say("a")
        focus.listener!!(FocusChange.GAIN)

        assertEquals(listOf("a"), engine.started)
        assertFalse(job.isCompleted)
        engine.finish()
    }

    @Test
    fun `CAN_DUCK이면 음량만 낮추고 계속 말한다`() = runTest {
        val job = say("a")
        focus.listener!!(FocusChange.LOSS_TRANSIENT_CAN_DUCK)

        assertEquals(1, engine.ducks)
        assertTrue(engine.speaking)
        assertFalse(job.isCompleted)
        engine.finish()
    }

    @Test
    fun `포커스를 잠시 잃은 동안 stop하면 돌아와도 다시 말하지 않는다`() = runTest {
        val job = say("a")
        focus.listener!!(FocusChange.LOSS_TRANSIENT)
        speaker.stop()
        assertTrue(job.isCompleted)

        focus.listener!!(FocusChange.GAIN)
        assertEquals(listOf("a"), engine.started)
        assertEquals(1, focus.abandons)
    }

    @Test
    fun `높은 우선순위는 말하던 낮은 문장을 끊고 기다리던 문장보다 먼저 말한다`() = runTest {
        val first = say("normal 1")
        val second = say("normal 2")
        val warning = say("warning", SpeechPriority.WARNING)

        assertTrue(first.isCompleted)
        assertEquals(listOf("normal 1", "warning"), engine.started)
        assertFalse(second.isCompleted)

        engine.finish()
        assertTrue(warning.isCompleted)
        assertEquals(listOf("normal 1", "warning", "normal 2"), engine.started)
        engine.finish()
        assertTrue(second.isCompleted)
    }

    @Test
    fun `같거나 낮은 우선순위는 말하던 문장이 끝날 때까지 기다린다`() = runTest {
        val warning = say("warning 1", SpeechPriority.WARNING)
        val sameLevel = say("warning 2", SpeechPriority.WARNING)
        val lower = say("normal")
        assertEquals(listOf("warning 1"), engine.started)

        engine.finish()
        assertTrue(warning.isCompleted)
        assertEquals(listOf("warning 1", "warning 2"), engine.started)
        engine.finish()
        assertTrue(sameLevel.isCompleted)
        assertEquals(listOf("warning 1", "warning 2", "normal"), engine.started)
        engine.finish()
        assertTrue(lower.isCompleted)
        assertEquals(0, engine.stops)
    }

    @Test
    fun `stop은 말하던 문장을 멈추고 기다리던 문장을 취소한다`() = runTest {
        val speaking = say("a")
        val waiting = say("b")
        speaker.stop()

        assertTrue(speaking.isCompleted)
        assertTrue(waiting.isCompleted)
        assertEquals(listOf("a"), engine.started)
        assertEquals(1, focus.abandons)

        // 그 뒤의 새 문장은 정상적으로 말합니다.
        val next = say("c")
        assertEquals(listOf("a", "c"), engine.started)
        engine.finish()
        assertTrue(next.isCompleted)
    }

    @Test
    fun `엔진이 끝을 알려 주지 않아도 1분 뒤에는 다음 문장으로 넘어간다`() = runTest {
        val stuck = launch { speaker.speak("a", SpeechPriority.NORMAL) }
        val next = launch { speaker.speak("b", SpeechPriority.NORMAL) }
        advanceTimeBy(61_000)

        assertTrue(stuck.isCompleted)
        assertEquals(listOf("a", "b"), engine.started)
        engine.finish()
        advanceTimeBy(1)
        assertTrue(next.isCompleted)
    }
}
