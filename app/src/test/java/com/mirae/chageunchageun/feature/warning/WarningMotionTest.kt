package com.mirae.chageunchageun.feature.warning

import androidx.compose.ui.graphics.luminance
import com.mirae.chageunchageun.core.model.Severity
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningMotionTest {

    @Test
    fun `주행 규제 중에는 스크롤하지 않는다`() {
        assertFalse(WarningMotion.scrolls(canShowDetail = false))
        assertTrue(WarningMotion.scrolls(canShowDetail = true))
    }

    @Test
    fun `주행 규제 중이거나 애니메이션 배율이 0이면 진입 애니메이션이 없다`() {
        assertFalse(WarningMotion.animatesEntry(canShowDetail = false, animatorScale = 1f))
        assertFalse(WarningMotion.animatesEntry(canShowDetail = true, animatorScale = 0f))
        assertTrue(WarningMotion.animatesEntry(canShowDetail = true, animatorScale = 1f))
    }

    @Test
    fun `경고등은 세 번 깜빡인 뒤 켜진 채로 있다`() {
        assertEquals(1f, EntryAnimation.telltaleAlpha(0f), 0.01f)
        listOf(0.6f, 1.8f, 3.0f).forEach { assertEquals(0.2f, EntryAnimation.telltaleAlpha(it), 0.01f) }
        listOf(3.6f, 5f, 100f).forEach { assertEquals(1f, EntryAnimation.telltaleAlpha(it), 0f) }
    }

    @Test
    fun `조치 단계는 차례로 나타나고 끝나면 모두 보인다`() {
        assertEquals(0f, EntryAnimation.stepProgress(0f, 1), 0f)
        assertEquals(1f, EntryAnimation.stepProgress(0.5f, 0), 0f)
        assertTrue(EntryAnimation.stepProgress(0.4f, 0) > EntryAnimation.stepProgress(0.4f, 1))

        for (steps in 1..20) {
            val end = EntryAnimation.totalSeconds(steps)
            assertEquals(1f, EntryAnimation.telltaleAlpha(end), 0f)
            repeat(steps) { assertEquals(1f, EntryAnimation.stepProgress(end, it), 0f) }
        }
    }

    @Test
    fun `심각도 색 위의 글자는 대비 4점5 대 1 이상이다`() {
        Severity.entries.forEach { severity ->
            val (background, text) = severityColors(severity)
            val a = background.luminance()
            val b = text.luminance()
            val contrast = (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
            assertTrue("$severity contrast $contrast", contrast >= 4.5f)
        }
        assertEquals(3, Severity.entries.map { severityColors(it).first }.toSet().size)
    }
}
