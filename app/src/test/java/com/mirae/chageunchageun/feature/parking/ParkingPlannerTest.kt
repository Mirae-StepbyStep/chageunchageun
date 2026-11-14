package com.mirae.chageunchageun.feature.parking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class ParkingPlannerTest {

    private val g = VehicleGeometry()

    private fun plan(side: ParkSide) =
        planReversePerpendicular(Pose(), side, exampleScene(side), g)

    @Test
    fun goalIsInsideTargetStall() {
        listOf(ParkSide.RIGHT, ParkSide.LEFT).forEach { side ->
            val p = plan(side)
            val stall = exampleScene(side).targetStall.corners
            goalOutline(p, g).forEach { c ->
                assertTrue(c.x >= stall.minOf { it.x } - 1e-3f && c.x <= stall.maxOf { it.x } + 1e-3f)
                assertTrue(c.y >= stall.minOf { it.y } - 1e-3f && c.y <= stall.maxOf { it.y } + 1e-3f)
            }
        }
    }

    @Test
    fun targetDependsOnCarPositionNotOnSteering() {
        val p = plan(ParkSide.RIGHT)
        val atStart = trackPlan(p, Pose(), g)
        // 시작은 똑바로 후진 구간이라 목표는 중앙(0°)입니다.
        assertEquals(ParkPhase.STRAIGHT_BACK, atStart.phase)
        assertEquals(0f, atStart.targetDeg, 10f)

        // 1m 후진해도 아직 직진. 2.5m쯤부터 돌리기 시작합니다.
        val oneMeter = trackPlan(p, Pose(0f, -1f, 0f), g)
        assertEquals(ParkPhase.STRAIGHT_BACK, oneMeter.phase)
        assertEquals(0f, oneMeter.targetDeg, 10f)
        val turning = trackPlan(p, Pose(0f, -2.5f, 0f), g)
        assertEquals(ParkPhase.TURN_IN, turning.phase)
        assertTrue(turning.targetDeg > 45f)

        // 원호 한가운데에 있으면 원호를 만드는 조향각에 가깝습니다.
        val mid = p.points.indexOfFirst { it.phase == ParkPhase.TURN_IN } +
            p.points.count { it.phase == ParkPhase.TURN_IN } / 2
        val onArc = trackPlan(p, p.points[mid].pose, g, mid)
        assertEquals(ParkPhase.TURN_IN, onArc.phase)
        assertEquals(p.turnSteeringDeg, onArc.targetDeg, 30f)
    }

    @Test
    fun leftAndRightGiveOppositeTargets() {
        val r = plan(ParkSide.RIGHT)
        val l = plan(ParkSide.LEFT)
        assertTrue(r.turnSteeringDeg > 0f)
        assertTrue(l.turnSteeringDeg < 0f)
        assertEquals(r.turnSteeringDeg, -l.turnSteeringDeg, 1e-3f)
    }

    /** 운전자가 안내를 따라 핸들을 돌리며(초당 400°까지) 후진하면 주차칸에 들어갑니다. */
    @Test
    fun followingGuidanceParksTheCar() {
        listOf(ParkSide.RIGHT, ParkSide.LEFT).forEach { side ->
            val p = plan(side)
            var pose = Pose()
            var steer = 0f
            var index = 0
            var status = trackPlan(p, pose, g, index)
            val phasesSeen = mutableSetOf<ParkPhase>()
            var guard = 0
            while (!status.done && guard++ < 2000) {
                phasesSeen += status.phase
                val maxStep = 400f * 0.083f
                steer += (status.targetDeg - steer).coerceIn(-maxStep, maxStep)
                pose = advancePose(pose, -0.05f, steer, g)
                index = status.index
                status = trackPlan(p, pose, g, index)
            }
            assertTrue("$side done", status.done)
            assertEquals(p.goal.x, pose.x, 0.25f)
            assertEquals(p.goal.y, pose.y, 0.25f)
            assertEquals(p.goal.headingRad, pose.headingRad, (5.0 * PI / 180).toFloat())
            assertTrue(phasesSeen.containsAll(listOf(ParkPhase.STRAIGHT_BACK, ParkPhase.TURN_IN, ParkPhase.STRAIGHTEN)))
        }
    }

    /** 운전자가 늦게 돌려 경로에서 벗어나도, 차 위치로 목표를 다시 계산해 바로잡습니다. */
    @Test
    fun lateDriverIsCorrected() {
        val p = plan(ParkSide.RIGHT)
        var pose = Pose()
        var steer = 0f
        var status = trackPlan(p, pose, g)
        var guard = 0
        while (!status.done && guard++ < 2000) {
            val maxStep = 400f * 0.083f
            if (guard > 30) steer += (status.targetDeg - steer).coerceIn(-maxStep, maxStep)
            pose = advancePose(pose, -0.05f, steer, g)
            status = trackPlan(p, pose, g, status.index)
        }
        assertTrue(status.done)
        assertEquals(p.goal.headingRad, pose.headingRad, (10.0 * PI / 180).toFloat())
    }
}
