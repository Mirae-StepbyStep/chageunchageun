package com.mirae.chageunchageun.core.port

import com.mirae.chageunchageun.core.model.CarSize
import com.mirae.chageunchageun.core.model.Signal

/** B1 차량 크기. 담당: 민서 */
interface VehicleSpecSource {
    suspend fun exteriorSize(): Signal<CarSize>
}
