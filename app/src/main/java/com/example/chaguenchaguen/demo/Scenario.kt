package com.example.chaguenchaguen.demo

import com.example.chaguenchaguen.core.model.Gear
import com.example.chaguenchaguen.core.model.TurnSignal
import com.example.chaguenchaguen.core.model.WarningType
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 시연 시나리오. assets/scenarios 폴더의 JSON 파일에 시간순으로 적습니다.
 * 적은 항목만 바뀌고 나머지는 그대로 유지됩니다.
 */
@Serializable
data class Scenario(val name: String, val steps: List<ScenarioStep>)

@Serializable
data class ScenarioStep(
    /** 시나리오 시작부터의 시각(ms). */
    val atMs: Long,
    val note: String? = null,
    val gear: Gear? = null,
    val speedMps: Float? = null,
    val brakePressed: Boolean? = null,
    val turnSignal: TurnSignal? = null,
    val steeringAngleDeg: Float? = null,
    val uxRestricted: Boolean? = null,
    val warnings: Set<WarningType>? = null,
    val arrivedAt: String? = null,
    val heard: String? = null,
)

object ScenarioParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Scenario = json.decodeFromString(Scenario.serializer(), text)
}

/** 시나리오를 시간에 맞춰 [FakeVehicleBus]에 적용합니다. 취소하면 그 시점에서 멈춥니다. */
class ScenarioPlayer(private val bus: FakeVehicleBus) {

    suspend fun play(scenario: Scenario, onStep: (ScenarioStep) -> Unit = {}) {
        var elapsed = 0L
        for (step in scenario.steps.sortedBy { it.atMs }) {
            delay((step.atMs - elapsed).coerceAtLeast(0))
            elapsed = step.atMs
            apply(step)
            onStep(step)
        }
    }

    fun apply(step: ScenarioStep) {
        step.gear?.let { bus.gear.value = it }
        step.speedMps?.let { bus.speedMps.value = it }
        step.brakePressed?.let { bus.brakePressed.value = it }
        step.turnSignal?.let { bus.turnSignal.value = it }
        step.steeringAngleDeg?.let { bus.steeringAngleDeg.value = it }
        step.uxRestricted?.let { bus.uxRestricted.value = it }
        step.warnings?.let { bus.warnings.value = it }
        step.arrivedAt?.let { bus.arrivals.tryEmit(it) }
        step.heard?.let { bus.heard.tryEmit(it) }
    }
}
