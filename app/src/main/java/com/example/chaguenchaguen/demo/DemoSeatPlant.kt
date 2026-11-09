package com.example.chaguenchaguen.demo

import com.example.chaguenchaguen.core.model.MoveDirection
import com.example.chaguenchaguen.core.model.SeatAxis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 시트 모터 흉내. FORWARD/BACKWARD를 받으면 [tickMillis]마다 1씩 움직이고 STOP에서 멈춥니다.
 * 실제 시트가 없어도 A1의 "위치 구독 → STOP" 제어 로직을 그대로 돌려 볼 수 있습니다.
 */
class DemoSeatPlant(
    private val bus: FakeVehicleBus,
    private val scope: CoroutineScope,
    private val tickMillis: Long = 60,
) {
    private val jobs = mutableMapOf<SeatAxis, Job>()

    fun command(axis: SeatAxis, direction: MoveDirection) {
        jobs.remove(axis)?.cancel()
        val step = when (direction) {
            MoveDirection.FORWARD -> 1
            MoveDirection.BACKWARD -> -1
            MoveDirection.STOP -> return
        }
        jobs[axis] = scope.launch {
            while (isActive) {
                delay(tickMillis)
                val current = bus.seat.value.getValue(axis)
                val next = (current + step).coerceIn(0, 100)
                if (next == current) break
                bus.seat.value = bus.seat.value + (axis to next)
            }
        }
    }
}
