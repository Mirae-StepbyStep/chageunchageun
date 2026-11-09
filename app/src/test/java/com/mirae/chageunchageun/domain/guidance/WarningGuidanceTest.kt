package com.mirae.chageunchageun.domain.guidance

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
}
