package com.mirae.chageunchageun.feature.warning

import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.SituationEvent
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.SpeechPriority
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.core.port.SpeechOutput
import com.mirae.chageunchageun.core.port.WarningSource
import com.mirae.chageunchageun.domain.guidance.WarningGuide
import com.mirae.chageunchageun.domain.guidance.WarningGuidance
import com.mirae.chageunchageun.runtime.DriveRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 새로 켜진 경고를 알립니다. 화면이 닫혀 있어도 말해야 하므로 화면 상태와 분리합니다.
 * 경고 문장은 E1 축소 대상이 아니므로 다시 켜지면 같은 문장을 그대로 말합니다.
 */
class WarningAnnouncer(
    private val warnings: WarningSource,
    private val speech: SpeechOutput,
    private val runtime: DriveRuntime,
) {
    suspend fun run() = coroutineScope {
        var known = emptySet<WarningType>()
        // 경고별로 말하는 중이거나 말할 차례를 기다리는 작업.
        val speaking = mutableMapOf<WarningType, Job>()
        // ponytail: 순서대로 말하므로 위험 경고도 재생 중인 주의 문장이 끝난 뒤 나옵니다. 끊어야 하면 실제 TTS 어댑터와 함께 바꿉니다.
        warnings.activeWarnings.combine(runtime.phase, ::Pair).collect { (signal, phase) ->
            // 값을 못 받은 것은 해제가 아닙니다.
            if (signal !is Signal.Value) return@collect
            val active = WarningGuidance.relevant(signal.value, phase)
            val added = WarningGuidance.ordered(active - known)
            // 꺼진 경고의 문장이 계속 나오지 않게 멈춥니다. 다른 경고의 문장은 건드리지 않습니다.
            (known - active).forEach { cleared ->
                val job = speaking.remove(cleared)
                if (job?.isActive == true) {
                    job.cancel()
                    if (speaking.values.none { it.isActive }) speech.stop()
                }
            }
            known = active
            added.forEach { guide ->
                report(guide, signal.source)
                // 말하는 동안에도 해제를 바로 알 수 있게 따로 실행합니다. 시작한 순서대로 말합니다.
                speaking[guide.type] = launch { say(guide) }
            }
        }
    }

    private fun report(guide: WarningGuide, source: Source) {
        runtime.report(
            SituationEvent(
                id = WarningGuidance.eventId(guide.type),
                severity = guide.severity,
                facts = mapOf("source" to source.name),
            ),
        )
    }

    private suspend fun say(guide: WarningGuide) {
        val text = WarningGuidance.speech(guide) ?: return
        try {
            speech.speak(text, SpeechPriority.WARNING)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 음성이 실패해도 화면 안내와 다음 경고는 계속되어야 합니다.
        }
    }
}
