package com.mirae.chageunchageun.domain.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeGuideTest {

    private val on = PracticeReading.Switch(on = true)
    private val off = PracticeReading.Switch(on = false)

    private fun PracticeSession.read(vararg readings: PracticeReading): PracticeSession =
        readings.fold(this, PracticeGuide::onReading)

    @Test
    fun `켜고 끄면 완료된다`() {
        val session = PracticeGuide.start(PracticeItem.HIGH_BEAM).read(off, on, off)
        assertTrue(session.isDone)
    }

    @Test
    fun `켜기만 하면 끄기 단계가 남는다`() {
        val session = PracticeGuide.start(PracticeItem.WIPER).read(off, on)
        assertFalse(session.isDone)
        assertEquals(PracticeTarget.OFF, session.current?.target)
        assertEquals("와이퍼가 움직여요.", session.lastConfirmed?.confirmed)
    }

    @Test
    fun `이미 켜져 있으면 먼저 끄는 단계부터 시작한다`() {
        val started = PracticeGuide.start(PracticeItem.HIGH_BEAM).read(on)
        assertEquals(3, started.stages.size)
        assertEquals(0, started.index)
        assertEquals(PracticeTarget.OFF, started.current?.target)

        assertTrue(started.read(off, on, off).isDone)
    }

    @Test
    fun `값을 못 받으면 넘어가지 않는다`() {
        val session = PracticeGuide.start(PracticeItem.BRAKE).read(PracticeReading.None, PracticeReading.None)
        assertEquals(0, session.index)
        assertFalse(session.primed)
    }

    @Test
    fun `직접 확인하면 한 단계 넘어간다`() {
        val session = PracticeGuide.confirmManually(PracticeGuide.start(PracticeItem.BRAKE))
        assertEquals(1, session.index)
        assertTrue(PracticeGuide.confirmManually(session).isDone)
    }

    @Test
    fun `직접 확인한 뒤에 켜진 값이 와도 먼저 끄기 단계를 붙이지 않는다`() {
        val session = PracticeGuide.confirmManually(PracticeGuide.start(PracticeItem.WIPER)).read(on)
        assertEquals(2, session.stages.size)
        assertEquals(1, session.index)
    }

    @Test
    fun `핸들은 왼쪽, 오른쪽, 가운데 순서로 확인한다`() {
        fun angle(deg: Float) = PracticeReading.Angle(deg)
        val start = PracticeGuide.start(PracticeItem.STEERING)

        // 가운데에서 시작해도, 오른쪽을 먼저 돌려도 첫 단계는 넘어가지 않습니다.
        assertEquals(0, start.read(angle(0f), angle(40f)).index)
        assertEquals(1, start.read(angle(0f), angle(-20f)).index)
        assertTrue(start.read(angle(0f), angle(-20f), angle(30f), angle(2f)).isDone)
    }

    @Test
    fun `조금만 돌린 것은 돌린 것으로 보지 않는다`() {
        assertFalse(PracticeGuide.reached(PracticeTarget.LEFT, PracticeReading.Angle(-10f)))
        assertFalse(PracticeGuide.reached(PracticeTarget.CENTER, PracticeReading.Angle(8f)))
        assertTrue(PracticeGuide.reached(PracticeTarget.RIGHT, PracticeReading.Angle(15f)))
    }

    @Test
    fun `전조등 끄기만 건너뛸 수 있다`() {
        val skippable = PracticeItem.entries.flatMap { item -> item.stages.filter { it.skippable }.map { item to it.target } }
        assertEquals(listOf(PracticeItem.LOW_BEAM to PracticeTarget.OFF), skippable)
    }
}
