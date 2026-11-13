package com.mirae.chageunchageun.domain.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveGameTest {

    private val dt = 0.05f

    /** [until] 거리까지 달립니다. 입력은 그때그때의 상태를 보고 정합니다. */
    private fun drive(from: GameState, until: Float, input: (GameState) -> GameInput): GameState {
        var s = from
        var guard = 0
        while (s.distanceM < until && !s.finished && guard++ < 20_000) s = DriveGame.step(s, input(s), dt)
        return s
    }

    private val idle = GameInput(steerDeg = 0f, brake = false, lowBeam = false, wiper = false)

    @Test
    fun `아무것도 안 하면 일정 속도로 달리고 첫 장애물에 걸린다`() {
        val s = drive(DriveGame.start(), 200f) { idle }
        assertEquals(DriveGame.CRUISE_MPS, s.speedMps, 0.01f)
        assertEquals(GameOutcome.MISSED, s.outcomes[0])
    }

    @Test
    fun `핸들을 돌리면 장애물을 피한다`() {
        val s = drive(DriveGame.start(), 200f) { idle.copy(steerDeg = if (it.distanceM < 120f) -40f else 0f) }
        assertTrue(s.carX < -DriveGame.CLEARANCE)
        assertEquals(GameOutcome.GOOD, s.outcomes[0])
    }

    @Test
    fun `정지선 앞에서 멈춰 기다리면 성공하고, 발을 떼면 다시 달린다`() {
        val beforeLine = drive(DriveGame.start(), 300f) { idle }
        var s = beforeLine
        repeat(100) { s = DriveGame.step(s, idle.copy(brake = true), dt) }
        assertEquals(0f, s.speedMps, 0.001f)
        assertTrue(s.distanceM < 330f)
        assertEquals(GameOutcome.GOOD, s.outcomes[1])
        assertFalse(DriveGame.isStopSignalOn(s, 1))

        s = drive(s, 400f) { idle }
        assertEquals(DriveGame.CRUISE_MPS, s.speedMps, 0.01f)
    }

    @Test
    fun `정지 신호를 그냥 지나가면 놓친 것으로 남는다`() {
        val s = drive(DriveGame.start(), 340f) { idle }
        assertEquals(GameOutcome.MISSED, s.outcomes[1])
    }

    @Test
    fun `너무 일찍 멈추면 정지로 인정하지 않는다`() {
        var s = drive(DriveGame.start(), 200f) { idle }
        repeat(100) { s = DriveGame.step(s, idle.copy(brake = true), dt) }
        assertEquals(0f, s.speedMps, 0.001f)
        assertNull(s.outcomes[1])
    }

    @Test
    fun `터널에서 전조등을 켜면 성공, 안 켜면 놓친다`() {
        val on = drive(DriveGame.start(), 640f) { idle.copy(lowBeam = it.distanceM > 470f) }
        assertEquals(GameOutcome.GOOD, on.outcomes[2])

        val off = drive(DriveGame.start(), 640f) { idle }
        assertEquals(GameOutcome.MISSED, off.outcomes[2])
    }

    @Test
    fun `레버를 찾을 시간으로 터널에 들어선 뒤 8초를 준다`() {
        val late = drive(DriveGame.start(), 640f) { idle.copy(lowBeam = it.distanceM > 450f + 100f) }
        assertEquals(GameOutcome.GOOD, late.outcomes[2])
    }

    @Test
    fun `전조등을 켠 채로 시작하면 조작 없이 성공하지 않는다`() {
        val alwaysOn = drive(DriveGame.start(), 640f) { idle.copy(lowBeam = true) }
        assertEquals(GameOutcome.UNCHECKED, alwaysOn.outcomes[2])

        val offThenOn = drive(DriveGame.start(), 640f) { idle.copy(lowBeam = it.distanceM !in 300f..470f) }
        assertEquals(GameOutcome.GOOD, offThenOn.outcomes[2])
    }

    @Test
    fun `비 구간에서 와이퍼를 켜면 성공하고 조금 뒤 비가 그친다`() {
        val justOn = drive(DriveGame.start(), 770f) { idle.copy(wiper = it.distanceM > 760f) }
        assertEquals(GameOutcome.GOOD, justOn.outcomes[3])
        assertTrue(DriveGame.isRaining(justOn))

        val later = drive(justOn, 820f) { idle.copy(wiper = true) }
        assertFalse(DriveGame.isRaining(later))
        assertEquals("비가 그쳤습니다. 와이퍼를 꺼주세요.", DriveGame.prompt(later, idle.copy(wiper = true)))
    }

    @Test
    fun `와이퍼를 켜지 않으면 비가 구간 끝까지 온다`() {
        val s = drive(DriveGame.start(), 880f) { idle }
        assertTrue(DriveGame.isRaining(s))
        assertEquals(GameOutcome.MISSED, s.outcomes[3])
    }

    @Test
    fun `신호를 못 받으면 성공으로 치지 않고 확인 불가로 남긴다`() {
        val s = drive(DriveGame.start(), DriveGame.FINISH_M) { GameInput() }
        assertTrue(s.finished)
        // 조향각이 없으면 장애물을 내보내지 않고, 브레이크 신호가 없으면 차가 스스로 멈췄다가 갑니다.
        assertEquals(List(4) { GameOutcome.UNCHECKED }, DriveGame.track.indices.map { s.outcomes[it] })
    }

    @Test
    fun `조향각을 못 받으면 장애물 안내를 건너뛴다`() {
        val s = drive(DriveGame.start(), 100f) { idle.copy(steerDeg = null) }
        assertEquals(GameOutcome.UNCHECKED, s.outcomes[0])
        assertNull(DriveGame.currentEvent(s))
    }

    @Test
    fun `정지 신호 안내는 멀 때는 준비, 가까우면 지금 밟으라고 한다`() {
        val far = drive(DriveGame.start(), 240f) { idle }
        assertEquals("곧 정지 신호입니다. 브레이크를 준비해주세요.", DriveGame.prompt(far, idle))
        val near = drive(far, 290f) { idle }
        assertEquals("지금 브레이크! 정지선 앞에 멈춰주세요.", DriveGame.prompt(near, idle))
    }

    @Test
    fun `결과 문구는 3초쯤 보인 뒤 사라진다`() {
        val justPassed = drive(DriveGame.start(), 180f) { idle }
        assertEquals(0, DriveGame.currentEvent(justPassed))
        val later = drive(justPassed, 216f) { idle }
        assertNull(DriveGame.currentEvent(later))
    }

    @Test
    fun `끝까지 달리면 모든 상황에 결과가 남는다`() {
        val s = drive(DriveGame.start(), DriveGame.FINISH_M) { idle }
        assertTrue(s.finished)
        assertEquals(DriveGame.track.indices.toSet(), s.outcomes.keys)
    }
}
