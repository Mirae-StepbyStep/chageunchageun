package com.mirae.chageunchageun.feature.parking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseSimulationTest {

    private val g = VehicleGeometry()
    private fun newSim() = ReverseSimulation(geometry = { g })

    /** 핸들 가운데, 목표 없음, R 기어로 [seconds]초 동안 0.1초씩 진행. */
    private fun ReverseSimulation.run(seconds: Float, steering: Float = 0f, target: Float? = null) {
        var t = 0f
        while (t < seconds - 1e-4f) {
            tick(0.1f, steering, target, done = false, inReverse = true)
            t += 0.1f
        }
    }

    @Test
    fun defaultSpeedIsHalfKmh() {
        val sim = newSim()
        assertEquals(0.5f, sim.speedKmh, 1e-6f)
        assertEquals(0f, sim.snapshot().appliedSpeedMps, 1e-6f)
        sim.start(null)
        assertEquals(0.5f / 3.6f, sim.snapshot().appliedSpeedMps, 1e-5f)
    }

    @Test
    fun speedChangeIsAppliedAndClamped() {
        val sim = newSim()
        sim.setSpeedKmh(1.0f)
        sim.start(null)
        sim.run(1.0f)
        assertEquals(1.0f / 3.6f, sim.travelledM, 1e-3f)

        sim.setSpeedKmh(5f)
        assertEquals(SIM_MAX_SPEED_KMH, sim.speedKmh, 1e-6f)
        sim.setSpeedKmh(0f)
        assertEquals(SIM_MIN_SPEED_KMH, sim.speedKmh, 1e-6f)
    }

    @Test
    fun pauseKeepsPose() {
        val sim = newSim()
        sim.start(null)
        sim.run(0.5f)
        sim.pause()
        val pose = sim.pose
        val travelled = sim.travelledM
        sim.run(3f)
        assertEquals(SimState.PAUSED, sim.state)
        assertEquals(pose, sim.pose)
        assertEquals(travelled, sim.travelledM, 0f)
        assertEquals(0f, sim.snapshot().appliedSpeedMps, 0f)
    }

    @Test
    fun resumeContinuesFromPausedPoseWithoutJump() {
        val sim = newSim()
        sim.start(null)
        sim.run(0.5f)
        sim.pause()
        val paused = sim.pose
        assertTrue(sim.resume(0f, null))
        // 오래 멈췄다 돌아온 것처럼 큰 시간 간격이 와도 한 tick은 MAX_TICK_SEC만큼만 움직입니다.
        sim.tick(30f, 0f, null, done = false, inReverse = true)
        val moved = paused.y - sim.pose.y
        assertEquals(kmhToMps(SIM_DEFAULT_SPEED_KMH) * MAX_TICK_SEC, moved, 1e-4f)
        assertEquals(paused.x, sim.pose.x, 1e-6f)
    }

    @Test
    fun manualNudges() {
        val sim = newSim()
        assertTrue(sim.nudge(0.1f, 0f))
        assertEquals(-0.1f, sim.pose.y, 1e-5f)
        assertEquals(SimState.PAUSED, sim.state)
        assertTrue(sim.nudge(0.5f, 0f))
        assertEquals(-0.6f, sim.pose.y, 1e-5f)
        assertEquals(0.6f, sim.travelledM, 1e-5f)

        // 진행 중에는 수동 이동을 받지 않습니다.
        sim.resume(0f, null)
        assertFalse(sim.nudge(0.5f, 0f))
        assertEquals(-0.6f, sim.pose.y, 1e-5f)
    }

    @Test
    fun startingTwiceDoesNotStackSpeed() {
        val sim = newSim()
        sim.start(null)
        sim.start(null)
        sim.start(null)
        sim.run(1f)
        assertEquals(kmhToMps(SIM_DEFAULT_SPEED_KMH), sim.travelledM, 1e-4f)
    }

    @Test
    fun waitForSteerHoldsUntilOnTargetAndResume() {
        val sim = newSim()
        sim.setWaitForSteer(true)
        sim.start(540f)

        // 핸들이 목표(540°)와 멀면 움직이지 않고 조향 대기.
        sim.tick(0.1f, 0f, 540f, done = false, inReverse = true)
        assertEquals(SimState.WAITING_STEER, sim.state)
        assertEquals(Pose(), sim.pose)

        // 목표 밖에서는 재개되지 않습니다.
        assertFalse(sim.resume(0f, 540f))
        assertEquals(SimState.WAITING_STEER, sim.state)

        // 목표 구간 안에서 재개하면 움직입니다.
        assertTrue(sim.resume(520f, 540f))
        sim.tick(0.1f, 520f, 540f, done = false, inReverse = true)
        assertEquals(SimState.RUNNING, sim.state)
        assertTrue(sim.travelledM > 0f)

        // 목표가 크게 바뀌면(+140°) 핸들이 그 목표에 맞아 있어도 다시 멈춥니다.
        sim.tick(0.1f, 400f, 400f, done = false, inReverse = true)
        assertEquals(SimState.WAITING_STEER, sim.state)
    }

    @Test
    fun resetRestoresStartButKeepsSettings() {
        val sim = newSim()
        sim.setSpeedKmh(1.2f)
        sim.setWaitForSteer(true)
        sim.start(null)
        sim.run(1f)
        sim.reset()
        assertEquals(SimState.IDLE, sim.state)
        assertEquals(Pose(), sim.pose)
        assertEquals(0f, sim.travelledM, 0f)
        assertEquals(0f, sim.snapshot().appliedSpeedMps, 0f)
        assertEquals(1.2f, sim.speedKmh, 1e-6f)
        assertTrue(sim.waitForSteer)
    }

    @Test
    fun stopsAutomaticallyWhenParked() {
        val sim = newSim()
        sim.start(null)
        sim.run(0.5f)
        sim.tick(0.1f, 0f, null, done = true, inReverse = true)
        assertEquals(SimState.DONE, sim.state)
        assertEquals(0f, sim.snapshot().appliedSpeedMps, 0f)
        val pose = sim.pose
        sim.run(2f)
        assertEquals(pose, sim.pose)
        // 완료 뒤에는 시작이 무시됩니다(처음 위치로 초기화해야 함).
        sim.start(null)
        assertEquals(SimState.DONE, sim.state)
    }

    @Test
    fun leavingReversePauses() {
        val sim = newSim()
        sim.start(null)
        sim.tick(0.1f, 0f, null, done = false, inReverse = false)
        assertEquals(SimState.PAUSED, sim.state)
    }

    @Test
    fun steeringAloneNeverMovesTheCar() {
        val sim = newSim()
        repeat(20) { sim.tick(0.1f, 540f, 540f, done = false, inReverse = true) }
        assertEquals(Pose(), sim.pose)
    }
}
