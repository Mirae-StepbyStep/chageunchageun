package com.mirae.chageunchageun.platform.car

import android.car.Car
import android.content.Context

/**
 * AAOS Car 서비스 연결 하나를 platform/car 어댑터들이 함께 씁니다.
 * 차량용 기기가 아니거나 연결에 실패하면 [manager]가 예외를 던지고, 어댑터는 이를 Unavailable로 내보냅니다.
 *
 * AAOS 타입(android.car.*)은 platform/car 밖으로 내보내지 않습니다.
 */
class CarSession(private val context: Context) {

    private var car: Car? = null

    @Synchronized
    private fun car(): Car = car ?: checkNotNull(Car.createCar(context.applicationContext)) {
        "Car service not available"
    }.also { car = it }

    /** [serviceName]은 Car.PROPERTY_SERVICE 같은 이름입니다. */
    fun <T> manager(serviceName: String, type: Class<T>): T =
        checkNotNull(type.cast(car().getCarManager(serviceName))) { "$serviceName not available" }
}
