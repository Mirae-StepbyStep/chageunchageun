package com.mirae.chageunchageun.platform.speech

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import com.mirae.chageunchageun.core.model.SpeechPriority
import com.mirae.chageunchageun.core.port.SpeechOutput
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "PleosSpeechOutput"

/**
 * Pleos TextToSpeech. 담당: E2 담당
 * 생성자는 AppGraph에 이미 연결돼 있으니 바꾸지 않습니다.
 * 11/12: C1(재균)이 Pleos TextToSpeech SDK 3.3.1의 ON_DEVICE 모드로 본문을 채웠습니다(앱 등록·네트워크 없이 동작).
 *
 * - 오디오 포커스와 우선순위 규칙은 [FocusedSpeaker]에 있습니다.
 * - 초기화에 실패하면 speak()은 소리 없이 바로 반환합니다. 화면 안내는 그대로 동작합니다.
 * - 음성은 Pleos 음성 서비스 프로세스에서 재생되고 SDK에 음량 조절이 없어, 덕킹 요청에는 음량을 낮추지 못합니다.
 *
 * Pleos·Android 오디오 타입은 이 파일 밖으로 내보내지 않습니다.
 */
class PleosSpeechOutput(private val context: Context) : SpeechOutput {

    // 권한이 필요 없는 주행 안내용 usage. TTS와 포커스 요청에 같은 객체를 씁니다.
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val speaker = FocusedSpeaker(PleosTtsEngine(context, attributes), AndroidAudioFocus(context, attributes))

    override suspend fun speak(text: String, priority: SpeechPriority) = speaker.speak(text, priority)

    override fun stop() = speaker.stop()
}

private class PleosTtsEngine(private val context: Context, private val attributes: AudioAttributes) : SpeechEngine {

    private var tts: TextToSpeech? = null
    private var failureLogged = false

    private val lock = Any()
    private var onEnd: (() -> Unit)? = null
    private var started = false

    override suspend fun prepare(): Boolean {
        if (tts != null) return true
        val ready = try {
            withContext(Dispatchers.Main) { withTimeoutOrNull(INIT_TIMEOUT_MS) { initialize() } } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "init threw ${e.javaClass.simpleName}: ${e.message}")
            false
        }
        if (!ready && !failureLogged) {
            failureLogged = true
            Log.w(TAG, "Pleos TTS is not available; warnings are shown without voice")
        }
        return ready
    }

    private suspend fun initialize(): Boolean = suspendCancellableCoroutine { continuation ->
        val engine = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                // 리스너는 initialize 뒤에 붙여야 합니다(그 전에는 SDK 내부가 준비되지 않아 예외).
                engine.addEventListener(events)
                engine.setLocale(Locale.KOREA) // "ko"만 주면 엔진이 지원하지 않는 로캘로 봅니다.
                engine.setAudioAttributes(attributes)
                tts = engine
                Log.i(TAG, "init onSuccess, sdk=${TextToSpeech.version}")
                if (continuation.isActive) continuation.resume(true)
            }

            override fun onFailure(error: ErrorType) {
                Log.w(TAG, "init onFailure $error")
                if (continuation.isActive) continuation.resume(false)
            }
        })
    }

    /**
     * SDK 이벤트에는 문장 식별자가 없습니다. 에뮬레이터에서 확인한 순서에 맞춥니다.
     * - stop() 뒤의 onStop은 다음 문장의 onStart보다 먼저 옵니다. 그래서 onStart를 본 뒤의 onDone·onStop만 끝으로 봅니다.
     * - 말하고 있지 않을 때 stop()을 불러도 onStop이 옵니다.
     */
    private val events = object : EventListener {
        override fun onReady() = Unit

        override fun onStart() {
            Log.i(TAG, "tts onStart")
            synchronized(lock) { started = true }
        }

        override fun onDone() {
            Log.i(TAG, "tts onDone")
            end(onlyIfStarted = true)
        }

        override fun onStop() {
            Log.i(TAG, "tts onStop")
            end(onlyIfStarted = true)
        }

        override fun onError(error: ErrorType) {
            Log.w(TAG, "tts onError $error")
            end(onlyIfStarted = false)
        }

        override fun onUpdatedRms(rms: Double) = Unit
    }

    private fun end(onlyIfStarted: Boolean) {
        val callback = synchronized(lock) {
            if (onlyIfStarted && !started) return
            started = false
            onEnd.also { onEnd = null }
        }
        callback?.invoke()
    }

    override fun start(text: String, onEnd: () -> Unit) {
        synchronized(lock) {
            this.onEnd = onEnd
            started = false
        }
        try {
            Log.i(TAG, "speak: $text")
            checkNotNull(tts).speak(text, true)
        } catch (e: Exception) {
            Log.w(TAG, "speak threw ${e.javaClass.simpleName}: ${e.message}")
            end(onlyIfStarted = false)
        }
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    // ponytail: SDK에 음량 조절이 없고 재생이 다른 프로세스라 낮출 수 없습니다. SDK가 지원하면 여기서 낮춥니다.
    override fun duck() {
        Log.i(TAG, "duck requested; the Pleos TTS SDK has no volume control, speaking at normal volume")
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 10_000L
    }
}

private class AndroidAudioFocus(context: Context, private val attributes: AudioAttributes) : AudioFocus {

    private val manager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    override fun request(onChange: (FocusChange) -> Unit): Boolean {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { code ->
                val change = when (code) {
                    AudioManager.AUDIOFOCUS_GAIN -> FocusChange.GAIN
                    AudioManager.AUDIOFOCUS_LOSS -> FocusChange.LOSS
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.LOSS_TRANSIENT
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusChange.LOSS_TRANSIENT_CAN_DUCK
                    else -> null
                }
                Log.i(TAG, "audio focus change $code -> $change")
                change?.let(onChange)
            }
            .build()
        val granted = manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(TAG, "audio focus request granted=$granted")
        if (granted) request = focusRequest
        return granted
    }

    override fun abandon() {
        request?.let {
            manager.abandonAudioFocusRequest(it)
            Log.i(TAG, "audio focus abandoned")
        }
        request = null
    }
}
