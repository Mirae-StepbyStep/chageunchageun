package com.mirae.chageunchageun.feature.parking

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 후진 직각주차 조향 목표 계산(시뮬레이션). 순수 Kotlin.
 *
 * 흐름
 * 1. 주차칸 방향 선택 → [exampleScene]의 가상 주차칸
 * 2. 차량 크기 → [VehicleGeometry] (휠베이스·조향비는 가정값)
 * 3. 목표 주차 위치 → [ParkingPlan.goal]
 * 4. 예상 경로 계산 → [planReversePerpendicular]: 똑바로 후진 → 원호로 돌아 넣기 → 풀고 똑바로
 * 5~7. 현재 위치에서 경로를 따라가려면 핸들을 얼마나 돌려야 하는지 매번 다시 계산 → [trackPlan]
 *      (pure pursuit: 차 뒤쪽 [LOOK_AHEAD_M] 앞의 경로 점을 지나는 원호의 곡률을 핸들 각도로 바꿈)
 * 8. 목표 위치 도달 → [PlanStatus.done]
 *
 * 카메라 영상으로 주차칸을 인식하지 않으므로 실제 차량에서 안전하다고 검증된 안내가 아닙니다.
 */

/** 차 뒤쪽으로 이만큼 떨어진 경로 점을 겨냥합니다(m). */
const val LOOK_AHEAD_M = 2.0f

/** 돌려 넣는 원호 반지름이 최소 회전 반지름의 몇 배인지. 1보다 커야 핸들 여유가 남습니다. */
private const val TURN_RADIUS_MARGIN = 1.12f

private const val PATH_STEP_M = 0.05f
private const val GOAL_REACHED_M = 0.15f

enum class ParkPhase(val stepNo: Int, val label: String) {
    STRAIGHT_BACK(1, "똑바로 후진"),
    TURN_IN(2, "돌려서 넣기"),
    STRAIGHTEN(3, "풀고 똑바로"),
    DONE(3, "주차 완료"),
}

/** 경로 위 한 점: 뒷바퀴 축 가운데의 월드 위치와 차 방향. */
data class PathPoint(val x: Float, val y: Float, val headingRad: Float, val phase: ParkPhase) {
    val pose: Pose get() = Pose(x, y, headingRad)
}

data class ParkingPlan(
    val points: List<PathPoint>,
    /** points에서 목표 위치의 번호. 그 뒤는 겨냥점을 위한 연장선입니다. */
    val goalIndex: Int,
    val goal: Pose,
    val turnRadiusM: Float,
    val turnSteeringDeg: Float,
)

data class PlanStatus(
    /** 지금 맞춰야 할 핸들 각도. 10° 단위로 반올림. */
    val targetDeg: Float,
    val phase: ParkPhase,
    /** 경로상 가장 가까운 점 번호. 다음 계산 때 넘겨 줍니다(뒤로 되돌아가지 않게). */
    val index: Int,
    val distanceToGoalM: Float,
    val done: Boolean,
)

/** 핸들 각도(도)로 만들 수 있는 가장 작은 회전 반지름(m). */
fun minTurnRadius(g: VehicleGeometry): Float =
    1f / abs(curvature(MAX_STEERING_WHEEL_DEG, g))

/** 곡률(1/m) → 핸들 각도(도). [curvature]의 반대. ±최대 각도로 자릅니다. */
fun steeringForCurvature(k: Float, g: VehicleGeometry): Float {
    val wheelDeg = Math.toDegrees(atan((k * g.wheelbaseM).toDouble())).toFloat()
    return (wheelDeg * g.steeringRatio).coerceIn(-MAX_STEERING_WHEEL_DEG, MAX_STEERING_WHEEL_DEG)
}

/**
 * [start]에서 [scene]의 목표 주차칸으로 들어가는 기준 경로.
 * start는 주차칸 줄과 나란히 서 있다고 봅니다(시뮬레이션 시작 위치).
 */
fun planReversePerpendicular(
    start: Pose,
    side: ParkSide,
    scene: ParkingScene,
    g: VehicleGeometry,
): ParkingPlan {
    val sx = if (side == ParkSide.RIGHT) 1f else -1f
    val stall = scene.targetStall.corners
    val stallCenterY = stall.map { it.y }.average().toFloat()
    val nearX = stall.minOf { abs(it.x) }
    val farX = stall.maxOf { abs(it.x) }

    val radius = minTurnRadius(g) * TURN_RADIUS_MARGIN
    val turnSteering = steeringForCurvature(sx / radius, g)

    // 원호가 시작되는 지점: 원호 끝이 주차칸 가운데 선에 닿도록.
    val turnStartY = stallCenterY + radius

    // 목표 뒷바퀴 축 위치: 차 앞뒤가 주차칸 안에 들어오고 뒤쪽 여유가 0.3m 남도록.
    val front = g.wheelbaseM + g.frontOverhangM
    val goalAbsX = (farX - 0.3f - g.rearOverhangM)
        .coerceAtLeast(nearX + 0.2f + front)
    val goalX = start.x + sx * goalAbsX
    val goalHeading = -sx * (PI / 2).toFloat()

    val local = mutableListOf<PathPoint>()

    // 1) 똑바로 후진.
    var y = 0f
    val startLocalTurnY = turnStartY - start.y
    while (y > startLocalTurnY) {
        local += PathPoint(0f, y, 0f, ParkPhase.STRAIGHT_BACK)
        y -= PATH_STEP_M
    }

    // 2) 원호. 중심 (sx*R, turnStartY), 차 방향 0 → -sx*90°.
    val n = (radius * PI / 2 / PATH_STEP_M).roundToInt().coerceAtLeast(4)
    for (i in 0..n) {
        val phi = (i.toFloat() / n) * (PI / 2).toFloat()
        local += PathPoint(
            sx * radius - sx * radius * cos(phi),
            startLocalTurnY - radius * sin(phi),
            -sx * phi,
            ParkPhase.TURN_IN,
        )
    }

    // 3) 풀고 똑바로: 주차칸 가운데 선을 따라 목표까지.
    val arcEndX = sx * radius
    val lineY = startLocalTurnY - radius
    val goalLocalX = goalX - start.x
    var x = arcEndX
    while (sx * x < sx * goalLocalX - 1e-4f) {
        x += sx * PATH_STEP_M
        local += PathPoint(x, lineY, goalHeading, ParkPhase.STRAIGHTEN)
    }
    val goalIndex = local.lastIndex

    // 겨냥점을 위한 연장선(목표 뒤 3m).
    for (i in 1..60) {
        local += PathPoint(goalLocalX + sx * PATH_STEP_M * i, lineY, goalHeading, ParkPhase.DONE)
    }

    // start 기준 좌표를 월드로. start 방향이 0이면 그대로 평행 이동만 됩니다.
    val points = local.map { p ->
        val w = vehicleToWorld(start, Vec2(p.x, p.y))
        PathPoint(w.x, w.y, start.headingRad + p.headingRad, p.phase)
    }
    val goal = points[goalIndex].pose

    return ParkingPlan(points, goalIndex, goal, radius, turnSteering)
}

/**
 * 지금 위치에서 기준 경로를 따라가려면 필요한 핸들 각도.
 * 차가 움직일 때마다 다시 부르면 목표가 갱신됩니다. 핸들만 돌렸을 때는 결과가 바뀌지 않습니다.
 */
fun trackPlan(
    plan: ParkingPlan,
    pose: Pose,
    g: VehicleGeometry,
    lastIndex: Int = 0,
): PlanStatus {
    val pts = plan.points
    val lo = (lastIndex - 10).coerceAtLeast(0)
    val hi = (lastIndex + 80).coerceAtMost(pts.lastIndex)
    var best = lo
    var bestD = Float.MAX_VALUE
    for (i in lo..hi) {
        val d = hypot(pts[i].x - pose.x, pts[i].y - pose.y)
        if (d < bestD) {
            bestD = d
            best = i
        }
    }

    val distanceToGoal = hypot(plan.goal.x - pose.x, plan.goal.y - pose.y)
    val done = best >= plan.goalIndex || distanceToGoal < GOAL_REACHED_M
    if (done) {
        return PlanStatus(0f, ParkPhase.DONE, best, distanceToGoal, true)
    }

    var j = best
    var acc = 0f
    while (j + 1 < pts.size && acc < LOOK_AHEAD_M) {
        acc += hypot(pts[j + 1].x - pts[j].x, pts[j + 1].y - pts[j].y)
        j++
    }
    val aim = worldToVehicle(pose, Vec2(pts[j].x, pts[j].y))
    val d2 = aim.x * aim.x + aim.y * aim.y
    val k = if (d2 < 1e-4f) 0f else 2f * aim.x / d2
    val raw = steeringForCurvature(k, g)
    val rounded = (raw / 10f).roundToInt() * 10f

    // 단계는 "지금 겨냥하는 경로 점"으로 정합니다. 목표 각도를 만드는 구간과 단계 이름이 어긋나지 않게.
    // 겨냥점이 다음 구간에 막 들어갔지만 목표가 아직 거의 0°이면 이전 단계를 유지합니다.
    val aimPhase = if (pts[j].phase == ParkPhase.DONE) ParkPhase.STRAIGHTEN else pts[j].phase
    val phase = if (aimPhase != pts[best].phase && abs(rounded) < ON_TARGET_DEG) pts[best].phase else aimPhase

    return PlanStatus(rounded, phase, best, distanceToGoal, false)
}

/** 경로의 차 뒤 모서리 두 줄(월드 좌표). [fromIndex]부터 목표까지만. 두 카메라 화면이 함께 씁니다. */
fun plannedRearEdges(plan: ParkingPlan, fromIndex: Int, g: VehicleGeometry): List<List<Vec2>> {
    val slice = plan.points.subList(fromIndex.coerceIn(0, plan.goalIndex), plan.goalIndex + 1)
    return listOf(-g.widthM / 2f, g.widthM / 2f).map { off ->
        slice.map { vehicleToWorld(it.pose, Vec2(off, -g.rearOverhangM)) }
    }
}

/** 목표 위치에 놓인 차의 외곽(월드 좌표). */
fun goalOutline(plan: ParkingPlan, g: VehicleGeometry): List<Vec2> =
    vehicleOutline(g).map { vehicleToWorld(plan.goal, it) }
