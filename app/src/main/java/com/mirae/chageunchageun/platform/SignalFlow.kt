package com.mirae.chageunchageun.platform

import com.mirae.chageunchageun.core.model.NOT_YET
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** 어댑터가 SDK 콜백에서 받은 결과를 흘려보내는 곳. 어느 스레드에서 불러도 됩니다. */
interface SignalSink<T> {
    /** 값을 받음. null이면 Unavailable로 내보냅니다. */
    fun value(value: T?)

    /** 값을 못 받음(onFailed, 미지원, 권한 없음 등). */
    fun unavailable(reason: String)
}

/**
 * "현재 값 조회 + 변화 구독"을 Signal Flow 하나로 만듭니다. Pleos SDK와 AAOS Car API 어댑터가 함께 씁니다.
 *
 * 수집을 시작하면 이 순서로 동작합니다.
 * 1. [NOT_YET]을 먼저 내보냅니다(combine이 바로 돌도록).
 * 2. [register]로 리스너를 등록합니다. 등록을 푸는 함수를 돌려줍니다.
 * 3. [getCurrent]로 현재 값을 조회합니다. 리스너가 먼저 값을 줬다면 조회 결과는 버립니다.
 * 4. 수집이 끝나면 2에서 받은 함수로 등록을 풉니다.
 *
 * [getCurrent]나 [register]가 예외를 던지면 사유를 담은 Unavailable을 내보냅니다. 0이나 false로 바꾸지 않습니다.
 *
 * Pleos 예시:
 * ```
 * override val angleDeg = signalFlow(
 *     source = Source.PLEOS,
 *     getCurrent = { sink ->
 *         wheel().getCurrentSteeringWheelAngle({ sink.value(it) }, { sink.unavailable(it.reason()) })
 *     },
 *     register = { sink ->
 *         val listener = object : SteeringWheelAngleListener {
 *             override fun onSteeringWheelAngleUpdated(angle: Float?) = sink.value(angle)
 *         }
 *         wheel().registerSteeringWheelAngle(listener)
 *         return@signalFlow { wheel().unregisterSteeringWheelAngle(listener) } // 등록한 것과 같은 객체로 해제
 *     },
 * )
 * ```
 *
 * 수집자마다 리스너를 하나씩 등록합니다. 같은 Flow를 여러 곳에서 수집해도 됩니다.
 */
fun <T : Any> signalFlow(
    source: Source,
    getCurrent: (sink: SignalSink<T>) -> Unit,
    register: (sink: SignalSink<T>) -> (() -> Unit),
): Flow<Signal<T>> = callbackFlow {
    val updated = AtomicBoolean(false)

    fun sinkOf(fromListener: Boolean) = object : SignalSink<T> {
        override fun value(value: T?) = emit(
            if (value == null) Signal.Unavailable("null value") else Signal.Value(value, source),
        )

        override fun unavailable(reason: String) = emit(Signal.Unavailable(reason))

        private fun emit(signal: Signal<T>) {
            if (fromListener) updated.set(true) else if (updated.get()) return
            trySend(signal)
        }
    }

    trySend(NOT_YET)
    val unregister: (() -> Unit)? = try {
        register(sinkOf(fromListener = true))
    } catch (e: Exception) {
        trySend(Signal.Unavailable("register failed: ${e.reason()}"))
        null
    }
    if (unregister != null) {
        try {
            getCurrent(sinkOf(fromListener = false))
        } catch (e: Exception) {
            if (!updated.get()) trySend(Signal.Unavailable("get failed: ${e.reason()}"))
        }
    }
    awaitClose { runCatching { unregister?.invoke() } }
}.conflate()

/** Unavailable 사유로 쓸 짧은 문자열. */
fun Throwable.reason(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
