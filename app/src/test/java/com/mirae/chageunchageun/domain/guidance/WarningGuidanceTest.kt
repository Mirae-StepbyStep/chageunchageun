package com.mirae.chageunchageun.domain.guidance

import com.mirae.chageunchageun.core.model.DrivePhase
import com.mirae.chageunchageun.core.model.Severity
import com.mirae.chageunchageun.core.model.WarningType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningGuidanceTest {

    private val all = WarningType.entries.map(WarningGuidance::guide)
    private val forbidden = listOf(
        "즉시 정차", "바로 정차", "당장 정차", "즉시 멈", "바로 멈", "당장 멈", "지금 멈", "급정지하세요", "급정거하세요",
    )

    @Test
    fun `모든 경고에 제목 의미 조치가 있다`() {
        all.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.meaning.isNotBlank())
            assertTrue(it.driveAction.isNotBlank())
            assertTrue(it.parkedSteps.isNotEmpty())
        }
    }

    @Test
    fun `공기압은 주의이고 이벤트 id가 고정이다`() {
        assertEquals(Severity.CAUTION, WarningGuidance.guide(WarningType.TIRE_PRESSURE).severity)
        assertEquals("warning.tire_pressure", WarningGuidance.eventId(WarningType.TIRE_PRESSURE))
    }

    @Test
    fun `정보 경고는 말하지 않고 위험과 주의는 급정지를 말린다`() {
        all.forEach {
            val speech = WarningGuidance.speech(it)
            if (it.severity == Severity.INFO) {
                assertNull(speech)
            } else {
                assertTrue(it.type.name, speech!!.contains("급정지하지"))
            }
        }
    }

    @Test
    fun `위험 조치에는 119가 있다`() {
        all.filter { it.severity == Severity.DANGER }.forEach { guide ->
            assertTrue(guide.type.name, guide.parkedSteps.any { "119" in it })
        }
    }

    @Test
    fun `어떤 문구도 급정지를 지시하지 않는다`() {
        all.forEach { guide ->
            val texts = listOf(guide.title, guide.meaning, guide.driveAction) +
                guide.parkedSteps + listOfNotNull(WarningGuidance.speech(guide))
            texts.forEach { text ->
                forbidden.forEach { word -> assertFalse("$word: $text", word in text) }
            }
        }
    }

    @Test
    fun `음성 문장은 120자 이하다`() {
        all.mapNotNull(WarningGuidance::speech).forEach { assertTrue(it, it.length <= 120) }
    }

    @Test
    fun `위험 주의 정보 순서로 정렬한다`() {
        val ordered = WarningGuidance.ordered(
            setOf(WarningType.WASHER_FLUID, WarningType.TIRE_PRESSURE, WarningType.BRAKE_FLUID),
        )
        assertEquals(
            listOf(WarningType.BRAKE_FLUID, WarningType.TIRE_PRESSURE, WarningType.WASHER_FLUID),
            ordered.map { it.type },
        )
    }

    private fun texts(guide: WarningGuide): List<String> =
        listOf(guide.title, guide.meaning, guide.driveAction) + guide.parkedSteps + listOfNotNull(WarningGuidance.speech(guide))

    @Test
    fun `급정지는 말리는 문장에만 나오고 급제동 지시 표현은 없다`() {
        val suddenStopAsInstruction = Regex("급정지(?!하지 말)")
        all.flatMap(::texts).forEach { text ->
            assertFalse(text, suddenStopAsInstruction.containsMatchIn(text))
            listOf("급브레이크", "즉시 정차", "바로 멈추").forEach { word -> assertFalse("$word: $text", word in text) }
        }
    }

    @Test
    fun `공기압 문구에는 압력 수치가 없다`() {
        val pressureNumber = Regex("\\d\\s*(kPa|psi|bar)", RegexOption.IGNORE_CASE)
        texts(WarningGuidance.guide(WarningType.TIRE_PRESSURE)).forEach { assertFalse(it, pressureNumber.containsMatchIn(it)) }
    }

    @Test
    fun `공기압 조치에 바람이 빠졌으면 긴급출동이 있다`() {
        val steps = WarningGuidance.guide(WarningType.TIRE_PRESSURE).parkedSteps
        assertTrue(steps.any { "긴급출동" in it && "운전하지 말고" in it })
    }

    @Test
    fun `새 경고의 심각도`() {
        val expected = mapOf(
            WarningType.PARKING_BRAKE to Severity.CAUTION,
            WarningType.STEERING_ASSIST to Severity.DANGER,
            WarningType.ABS to Severity.CAUTION,
            WarningType.ESC to Severity.CAUTION,
            WarningType.ESC_OFF to Severity.INFO,
            WarningType.EV_SYSTEM to Severity.DANGER,
            WarningType.POWER_DOWN to Severity.CAUTION,
            WarningType.LOW_VOLT_BATTERY to Severity.CAUTION,
        )
        expected.forEach { (type, severity) -> assertEquals(type.name, severity, WarningGuidance.guide(type).severity) }
    }

    @Test
    fun `주차 브레이크 경고는 주차 중에만 뺀다`() {
        val types = setOf(WarningType.PARKING_BRAKE, WarningType.TIRE_PRESSURE)
        assertEquals(setOf(WarningType.TIRE_PRESSURE), WarningGuidance.relevant(types, DrivePhase.PARKED))
        assertEquals(types, WarningGuidance.relevant(types, DrivePhase.DRIVING))
    }
}
