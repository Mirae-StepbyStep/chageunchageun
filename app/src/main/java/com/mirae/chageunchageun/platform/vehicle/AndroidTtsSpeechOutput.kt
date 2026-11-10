
package com.mirae.chageunchageun.platform

import android.content.Context
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import com.mirae.chageunchageun.core.model.SpeechPriority
import com.mirae.chageunchageun.core.port.SpeechOutput
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Pleos TextToSpeech adapter.
 *
 * Uses the on-device speech engine without server registration.
 * Speech completion and errors are reported through SDK events.
 */
class AndroidTtsSpeechOutput(
    context: Context,
    private val onSpoken: (String) -> Unit = {}
) : SpeechOutput {

    companion object {
        private const val TAG = "PleosTTS"
    }

    private val engine = TextToSpeech(
        context.applicationContext,
        Mode.ON_DEVICE
    )

    private val initialized = CompletableDeferred<Unit>()
    private val speechMutex = Mutex()

    @Volatile
    private var pendingSpeech: CompletableDeferred<Unit>? = null

    private val eventListener = object : EventListener {

        override fun onReady() {
            Log.d(TAG, "Speech service ready")
        }

        override fun onStart() {
            Log.d(TAG, "Speech started")
        }

        override fun onUpdatedRms(rms: Double) = Unit

        override fun onDone() {
            Log.d(TAG, "Speech completed")
            pendingSpeech?.complete(Unit)
        }

        override fun onStop() {
            Log.d(TAG, "Speech stopped")
            pendingSpeech?.completeExceptionally(
                IllegalStateException("Speech stopped")
            )
        }

        override fun onError(errorType: ErrorType) {
            Log.e(TAG, "Speech error: $errorType")
            pendingSpeech?.completeExceptionally(
                IllegalStateException(
                    "Pleos TTS error: $errorType"
                )
            )
        }
    }

    init {
        Log.d(TAG, "Initializing Pleos TTS")

        engine.initialize(object : OnInitializeListener {

            override fun onSuccess() {
                try {
                    engine.addEventListener(eventListener)
                    engine.setLocale(Locale.KOREA)

                    Log.d(TAG, "Pleos TTS initialized")
                    initialized.complete(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "TTS setup failed", e)
                    initialized.completeExceptionally(e)
                }
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(
                    TAG,
                    "Pleos TTS initialization failed: $errorType"
                )

                initialized.completeExceptionally(
                    IllegalStateException(
                        "Pleos TTS initialization failed: $errorType"
                    )
                )
            }
        })
    }

    override suspend fun speak(
        text: String,
        priority: SpeechPriority
    ) {
        if (text.isBlank()) return

        try {
            withTimeout(15000L) {
                initialized.await()
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Pleos TTS initialization timed out",
                e
            )
        }

        speechMutex.withLock {
            val completion = CompletableDeferred<Unit>()
            pendingSpeech = completion

            try {
                Log.d(TAG, "Speech requested: $text")
                engine.speak(text)

                withTimeout(30000L) {
                    completion.await()
                }

                // Record only successfully completed speech.
                onSpoken(text)
            } finally {
                if (!completion.isCompleted) {
                    engine.stop()
                }

                pendingSpeech = null
            }
        }
    }

    override fun stop() {
        Log.d(TAG, "Stopping Pleos TTS")

        pendingSpeech?.completeExceptionally(
            IllegalStateException("Speech cancelled")
        )

        engine.stop()
    }
}
