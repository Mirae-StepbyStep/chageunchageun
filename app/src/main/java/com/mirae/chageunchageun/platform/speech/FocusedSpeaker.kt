package com.mirae.chageunchageun.platform.speech

import com.mirae.chageunchageun.core.model.SpeechPriority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** 오디오 포커스 변화. 운영사무국이 정한 네 가지입니다. */
internal enum class FocusChange { GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK }

/** 음성 엔진. 실제 구현은 PleosSpeechOutput.kt에 있고 테스트는 가짜를 씁니다. */
internal interface SpeechEngine {
    /** 말할 수 없으면(초기화 실패 등) false. 예외를 던지지 않습니다. */
    suspend fun prepare(): Boolean

    /** 말하기 시작. 끝나거나, 실패하거나, 멈추면 [onEnd]를 한 번 부릅니다. */
    fun start(text: String, onEnd: () -> Unit)

    fun stop()

    /** 남은 문장의 음량을 낮춥니다. 엔진이 못 하면 아무것도 하지 않습니다. */
    fun duck()
}

internal interface AudioFocus {
    /** 포커스를 요청합니다. 받지 못하면 false. 이후 변화는 [onChange]로 옵니다. */
    fun request(onChange: (FocusChange) -> Unit): Boolean

    fun abandon()
}

/**
 * 한 번에 한 문장만 말하게 하고, 오디오 포커스와 우선순위 규칙을 적용합니다. Android 타입이 없어 JVM 테스트로 확인합니다.
 *
 * - 말하기 전에 포커스를 요청하고, 못 받으면 말하지 않습니다. 문장이 끝나면 포커스를 돌려줍니다.
 * - LOSS: 바로 멈춥니다. LOSS_TRANSIENT: 멈췄다가 포커스가 돌아오면 처음부터 한 번 다시 말합니다.
 *   LOSS_TRANSIENT_CAN_DUCK: 음량을 낮춥니다.
 * - 더 높은 우선순위가 오면 말하던 문장을 끊습니다. 같거나 낮으면 끝날 때까지 기다립니다.
 */
internal class FocusedSpeaker(private val engine: SpeechEngine, private val focus: AudioFocus) {

    private class Utterance(val text: String, val priority: SpeechPriority) {
        /** 차례가 오면 true, stop()으로 취소되면 false. */
        val turn = CompletableDeferred<Boolean>()
        val finished = CompletableDeferred<Unit>()
        var hasFocus = false
        var plays = 0
        var playing = false
        var waitingForFocus = false
        var replayed = false
    }

    private val lock = Any()
    private var current: Utterance? = null
    private val waiting = mutableListOf<Utterance>()

    suspend fun speak(text: String, priority: SpeechPriority) {
        val utterance = Utterance(text, priority)
        synchronized(lock) {
            val speaking = current
            if (speaking == null) {
                current = utterance
                utterance.turn.complete(true)
            } else {
                // 높은 우선순위가 앞에 서고, 같은 우선순위 안에서는 온 순서대로입니다.
                val index = waiting.indexOfFirst { it.priority < priority }
                waiting.add(if (index < 0) waiting.size else index, utterance)
                if (priority > speaking.priority) speaking.finished.complete(Unit)
            }
        }
        try {
            if (!utterance.turn.await()) return
            if (!engine.prepare()) return
            synchronized(lock) {
                if (utterance.finished.isCompleted) return // stop()이나 더 높은 우선순위가 준비 중에 옴
                if (!focus.request { onFocusChange(utterance, it) }) return
                utterance.hasFocus = true
                play(utterance)
            }
            // 엔진이 끝을 알려 주지 않아도 다음 안내가 막히지 않게 합니다.
            withTimeoutOrNull(MAX_UTTERANCE_MS) { utterance.finished.await() }
        } finally {
            val next = synchronized(lock) {
                utterance.finished.complete(Unit)
                waiting.remove(utterance)
                if (utterance.playing) engine.stop()
                if (utterance.hasFocus) focus.abandon()
                if (current === utterance) waiting.removeFirstOrNull().also { current = it } else null
            }
            next?.turn?.complete(true)
        }
    }

    /** 말하던 문장을 멈추고, 기다리던 문장을 모두 취소합니다. */
    fun stop() {
        // Clear the queue first, so the stopped utterance cannot hand its turn to a waiting one.
        val (speaking, cancelled) = synchronized(lock) {
            current to waiting.toList().also { waiting.clear() }
        }
        cancelled.forEach { it.turn.complete(false) }
        speaking?.finished?.complete(Unit)
    }

    private fun play(utterance: Utterance) {
        val play = ++utterance.plays
        utterance.playing = true
        engine.start(utterance.text) {
            synchronized(lock) {
                if (utterance.plays != play) return@synchronized
                utterance.playing = false
                // 포커스를 잠시 잃어 멈춘 것이면 문장이 끝난 것이 아닙니다.
                if (!utterance.waitingForFocus) utterance.finished.complete(Unit)
            }
        }
    }

    private fun onFocusChange(utterance: Utterance, change: FocusChange) = synchronized(lock) {
        if (utterance.finished.isCompleted) return@synchronized
        when (change) {
            FocusChange.GAIN -> if (utterance.waitingForFocus) {
                utterance.waitingForFocus = false
                utterance.replayed = true
                play(utterance)
            }
            FocusChange.LOSS -> utterance.finished.complete(Unit)
            FocusChange.LOSS_TRANSIENT -> if (utterance.replayed) {
                utterance.finished.complete(Unit) // 다시 말하기는 한 번만
            } else {
                utterance.waitingForFocus = true
                engine.stop()
            }
            FocusChange.LOSS_TRANSIENT_CAN_DUCK -> engine.duck()
        }
        Unit
    }

    private companion object {
        const val MAX_UTTERANCE_MS = 60_000L
    }
}
