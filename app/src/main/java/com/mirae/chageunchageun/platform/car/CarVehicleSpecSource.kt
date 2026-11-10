package com.mirae.chageunchageun.platform.car

import com.mirae.chageunchageun.core.model.CarSize
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.port.VehicleSpecSource
import com.mirae.chageunchageun.platform.NOT_IMPLEMENTED

/**
 * 차량 외부 크기(INFO_EXTERIOR_DIMENSIONS)를 AAOS Car API로 읽습니다. 담당: 민서
 * 생성자는 AppGraph에 이미 연결돼 있으니 바꾸지 말고 본문만 채웁니다.
 */
class CarVehicleSpecSource(private val session: CarSession) : VehicleSpecSource {
    override suspend fun exteriorSize(): Signal<CarSize> = NOT_IMPLEMENTED
}
