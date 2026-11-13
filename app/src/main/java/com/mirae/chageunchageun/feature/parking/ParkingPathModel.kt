package com.mirae.chageunchageun.feature.parking

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/*
 * 서라운드뷰와 후방 카메라가 함께 쓰는 경로 계산. 순수 Kotlin(JVM 테스트 가능).
 *
 * 좌표(미터):
 * - 차량 좌표: 원점 = 뒷바퀴 축 가운데, x = 오른쪽, y = 앞쪽.
 * - 월드 좌표: 시뮬레이션 시작 위치의 차량 좌표. 주차칸 같은 주변 물체가 여기에 고정됩니다.
 *
 * 차량 위치(Pose)는 속도 신호나 개발자 "가상 후진"으로만 바뀝니다.
 * 핸들 각도만 바뀌면 경로선만 바뀌고 차는 움직이지 않습니다.
 */

data class Vec2(val x: Float, val y: Float)

/** 월드 좌표에서 차량의 위치와 방향. heading 0 = 월드 +y를 바라봄, 양수 = 시계 방향(오른쪽). */
data class Pose(val x: Float = 0f, val y: Float = 0f, val headingRad: Float = 0f)

/**
 * 경로 계산용 차량 제원.
 * 휠베이스·조향비·뒤 오버행은 검증 전 가정값이라 [isAssumed]가 true입니다.
 * 화면은 이 값이 true인 동안 "시뮬레이션 경로"로 표시합니다.
 */
data class VehicleGeometry(
    val wheelbaseM: Float = 2.9f,
    val steeringRatio: Float = 15f,
    val widthM: Float = 1.87f,
    val lengthM: Float = 4.66f,
    val rearOverhangM: Float = 1.0f,
    val isAssumed: Boolean = true,
) {
    val frontOverhangM: Float get() = lengthM - wheelbaseM - rearOverhangM

    companion object {
        /** 차 크기(mm)만 실제 값으로 받고 나머지는 가정값을 씁니다. */
        fun fromSize(lengthMm: Int, widthMm: Int): VehicleGeometry =
            VehicleGeometry(lengthM = lengthMm / 1000f, widthM = widthMm / 1000f)
    }
}

/** 핸들 각도(도) → 곡률(1/m). 양수 = 오른쪽으로 휨. 단순 자전거 모델입니다. */
fun curvature(steeringDeg: Float, g: VehicleGeometry): Float {
    val wheelRad = Math.toRadians((steeringDeg / g.steeringRatio).toDouble())
    return (tan(wheelRad) / g.wheelbaseM).toFloat()
}

/**
 * 뒷바퀴 축 가운데에서 [lateralOffsetM]만큼 옆에 있는 점이,
 * 차가 [distanceM]만큼 움직일 때(음수 = 후진) 지나는 위치. 차량 좌표.
 */
fun pointAlongArc(
    steeringDeg: Float,
    lateralOffsetM: Float,
    distanceM: Float,
    g: VehicleGeometry,
): Vec2 {
    val k = curvature(steeringDeg, g)
    if (abs(k) < 1e-4f) return Vec2(lateralOffsetM, distanceM)
    val r = 1f / k
    val phi = distanceM * k
    val rr = r - lateralOffsetM
    return Vec2(r - rr * cos(phi), rr * sin(phi))
}

/**
 * 후진 예상 경로: 차 폭 양쪽 끝 두 줄. 차량 좌표이며 뒤 범퍼에서 시작합니다.
 * 두 화면(서라운드뷰, 후방 카메라)이 같은 이 함수를 씁니다.
 */
fun reversePathEdges(
    steeringDeg: Float,
    g: VehicleGeometry,
    lengthM: Float = 5.5f,
    samples: Int = 24,
): List<List<Vec2>> = listOf(-g.widthM / 2f, g.widthM / 2f).map { offset ->
    (0..samples).map { i ->
        val p = pointAlongArc(steeringDeg, offset, -lengthM * i / samples, g)
        Vec2(p.x, p.y - g.rearOverhangM)
    }
}

/** 차가 [distanceM]만큼 움직인 뒤의 위치(음수 = 후진). 핸들 각도는 그동안 일정하다고 봅니다. */
fun advancePose(pose: Pose, distanceM: Float, steeringDeg: Float, g: VehicleGeometry): Pose {
    val local = pointAlongArc(steeringDeg, 0f, distanceM, g)
    val moved = vehicleToWorld(pose, local)
    val dh = distanceM * curvature(steeringDeg, g)
    return Pose(moved.x, moved.y, pose.headingRad + dh)
}

fun vehicleToWorld(pose: Pose, p: Vec2): Vec2 {
    val c = cos(pose.headingRad)
    val s = sin(pose.headingRad)
    return Vec2(pose.x + p.x * c + p.y * s, pose.y - p.x * s + p.y * c)
}

fun worldToVehicle(pose: Pose, w: Vec2): Vec2 {
    val dx = w.x - pose.x
    val dy = w.y - pose.y
    val c = cos(pose.headingRad)
    val s = sin(pose.headingRad)
    return Vec2(dx * c - dy * s, dx * s + dy * c)
}

/** 차량 외곽 네 꼭짓점(차량 좌표): 앞왼, 앞오, 뒤오, 뒤왼. */
fun vehicleOutline(g: VehicleGeometry): List<Vec2> {
    val half = g.widthM / 2f
    val front = g.wheelbaseM + g.frontOverhangM
    val rear = -g.rearOverhangM
    return listOf(Vec2(-half, front), Vec2(half, front), Vec2(half, rear), Vec2(-half, rear))
}

/** 직사각형 하나(월드 좌표, 네 꼭짓점). */
data class Box2(val corners: List<Vec2>)

/**
 * 시연용 주변 배치. 실제 센서로 인식한 주차칸이 아니라 예시입니다.
 * 차 오른쪽(또는 왼쪽) 뒤에 직각 주차칸이 있고, 양옆에 차가 서 있습니다.
 * 시작 위치에서 약 3.5m 똑바로 후진한 뒤 돌려 넣을 수 있는 거리에 둡니다.
 * (겨냥 거리 2m보다 직진 구간이 길어야 첫 단계 목표가 0°로 나옵니다.)
 */
data class ParkingScene(
    val targetStall: Box2,
    val neighborCars: List<Box2>,
    val stallLines: List<Pair<Vec2, Vec2>>,
)

fun exampleScene(side: ParkSide): ParkingScene {
    val sx = if (side == ParkSide.RIGHT) 1f else -1f
    val stallWidth = 2.5f
    val stallNear = 1.6f
    val stallFar = 6.9f
    val centerY = -8.0f

    fun stall(cy: Float, near: Float, far: Float, halfW: Float) = Box2(
        listOf(
            Vec2(sx * near, cy + halfW), Vec2(sx * far, cy + halfW),
            Vec2(sx * far, cy - halfW), Vec2(sx * near, cy - halfW),
        ),
    )

    val target = stall(centerY, stallNear, stallFar, stallWidth / 2f)
    val neighbors = listOf(-1, 1).map { i ->
        stall(centerY + i * stallWidth, stallNear + 0.5f, stallFar - 0.4f, 0.93f)
    }
    val lines = (-2..1).map { i ->
        val y = centerY + stallWidth / 2f + i * stallWidth
        Vec2(sx * stallNear, y) to Vec2(sx * stallFar, y)
    } + listOf(Vec2(sx * stallFar, centerY - 2.5f * stallWidth) to Vec2(sx * stallFar, centerY + 1.5f * stallWidth))

    return ParkingScene(target, neighbors, lines)
}
