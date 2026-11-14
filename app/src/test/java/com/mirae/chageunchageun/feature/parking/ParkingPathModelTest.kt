package com.mirae.chageunchageun.feature.parking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ParkingPathModelTest {

    private val g = VehicleGeometry()

    @Test
    fun centeredWheelGivesStraightPath() {
        val edges = reversePathEdges(0f, g)
        edges.forEach { edge ->
            val xs = edge.map { it.x }
            assertEquals(xs.first(), xs.last(), 1e-4f)
        }
        // 뒤 범퍼에서 시작해 뒤쪽(-y)으로 갑니다.
        assertEquals(-g.rearOverhangM, edges[0].first().y, 1e-4f)
        assertTrue(edges[0].last().y < edges[0].first().y)
    }

    @Test
    fun pathBendsTowardSteeringSideWhenReversing() {
        val right = reversePathEdges(540f, g)[0].last()
        val left = reversePathEdges(-540f, g)[0].last()
        val straight = reversePathEdges(0f, g)[0].last()
        assertTrue(right.x > straight.x)
        assertTrue(left.x < straight.x)
    }

    @Test
    fun steeringAloneDoesNotMoveCar() {
        val start = Pose()
        val after = advancePose(start, 0f, 540f, g)
        assertEquals(start.x, after.x, 1e-6f)
        assertEquals(start.y, after.y, 1e-6f)
        assertEquals(start.headingRad, after.headingRad, 1e-6f)
    }

    @Test
    fun reversingWithRightLockTurnsCarCounterClockwise() {
        var pose = Pose()
        repeat(100) { pose = advancePose(pose, -0.05f, 540f, g) }
        assertTrue(pose.headingRad < 0f)
        assertTrue(pose.x > 0f)
        assertTrue(pose.y < 0f)
    }

    @Test
    fun worldVehicleRoundTrip() {
        val pose = Pose(1.2f, -3.4f, (PI / 5).toFloat())
        val p = Vec2(0.7f, -2.1f)
        val back = worldToVehicle(pose, vehicleToWorld(pose, p))
        assertEquals(p.x, back.x, 1e-4f)
        assertEquals(p.y, back.y, 1e-4f)
    }

    @Test
    fun sceneMirrorsBySide() {
        val r = exampleScene(ParkSide.RIGHT).targetStall.corners.map { it.x }
        val l = exampleScene(ParkSide.LEFT).targetStall.corners.map { it.x }
        assertTrue(r.all { it > 0f })
        assertTrue(l.all { it < 0f })
    }
}
