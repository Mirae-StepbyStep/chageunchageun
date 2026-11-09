package com.mirae.chageunchageun.core.model

/**
 * 차량·위치·음성에서 온 값 하나. 기능 코드는 SDK 타입 대신 이 타입만 봅니다.
 *
 * - [Value]: 값을 받았음. [source]로 실제 신호인지 시연용 신호인지 구분합니다.
 * - [Unavailable]: 값을 못 받음(미지원, 권한 없음, 호출 실패, 아직 수신 전). 0이나 false로 바꾸지 않습니다.
 */
sealed interface Signal<out T> {
    data class Value<T>(
        val value: T,
        val source: Source,
        val atMillis: Long = System.currentTimeMillis(),
    ) : Signal<T>

    data class Unavailable(val reason: String) : Signal<Nothing>
}

/** 값의 출처. 화면과 기록에 함께 남겨 시뮬레이션 값을 실제 값처럼 보이지 않게 합니다. */
enum class Source { PLEOS, AAOS, NAVI, SCRIPTED }

fun <T> Signal<T>.valueOrNull(): T? = (this as? Signal.Value<T>)?.value

fun <T> Signal<T>.sourceOrNull(): Source? = (this as? Signal.Value<T>)?.source

val NOT_YET: Signal<Nothing> = Signal.Unavailable("not received yet")
