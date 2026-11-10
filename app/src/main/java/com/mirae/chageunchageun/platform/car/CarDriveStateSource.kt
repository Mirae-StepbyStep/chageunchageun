package com.mirae.chageunchageun.platform.car

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.port.DriveStateSource
import com.mirae.chageunchageun.platform.SignalSink
import com.mirae.chageunchageun.platform.signalFlow
import kotlinx.coroutines.flow.Flow

/**
 * 기어(GEAR_SELECTION)와 주행 규제(CarUxRestrictions)를 AAOS Car API로 읽습니다. 담당: 후
 * 다른 어댑터의 본보기입니다. [signalFlow]에 "현재 값 조회"와 "구독 등록·해제"만 넘깁니다.
 *
 * - 기어는 android.car.permission.CAR_POWERTRAIN 권한이 필요합니다(매니페스트 선언).
 * - 에뮬레이터 미시험: Virtual Console 조작에 따라 값이 바뀌는지 확인이 필요합니다.
 */
class CarDriveStateSource(private val session: CarSession) : DriveStateSource {

    private fun properties() = session.manager(Car.PROPERTY_SERVICE, CarPropertyManager::class.java)
    private fun uxManager() = session.manager(Car.CAR_UX_RESTRICTION_SERVICE, CarUxRestrictionsManager::class.java)

    override val gear: Flow<Signal<Gear>> = signalFlow(
        source = Source.AAOS,
        getCurrent = { sink ->
            sink.value(gearFromVehicleGear(properties().getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0)))
        },
        register = { sink ->
            val manager = properties()
            val callback = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    val raw = value.value as? Int
                    if (raw == null) sink.unavailable("gear value missing") else sink.value(gearFromVehicleGear(raw))
                }

                override fun onErrorEvent(propertyId: Int, areaId: Int) = sink.unavailable("gear property error")
            }
            // Android 14 기준 API. subscribePropertyEvents는 API 35부터라 쓰지 않습니다.
            @Suppress("DEPRECATION")
            val registered = manager.registerCallback(
                callback,
                VehiclePropertyIds.GEAR_SELECTION,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
            )
            check(registered) { "GEAR_SELECTION not supported" }
            return@signalFlow { manager.unregisterCallback(callback) }
        },
    )

    /**
     * CarUxRestrictionsManager는 리스너를 하나만 받습니다. 그래서 리스너 하나를 등록해 두고
     * 수집자들([uxSinks])에게 나눠 줍니다. 마지막 수집자가 끝나면 해제합니다.
     */
    private val uxSinks = LinkedHashSet<SignalSink<Boolean>>()

    override val uxRestricted: Flow<Signal<Boolean>> = signalFlow(
        source = Source.AAOS,
        getCurrent = { sink -> sink.value(uxManager().currentCarUxRestrictions?.isRequiresDistractionOptimization) },
        register = { sink ->
            val manager = uxManager()
            synchronized(uxSinks) {
                if (uxSinks.isEmpty()) {
                    manager.registerListener { restrictions ->
                        val restricted = restrictions.isRequiresDistractionOptimization
                        synchronized(uxSinks) { uxSinks.toList() }.forEach { it.value(restricted) }
                    }
                }
                uxSinks += sink
            }
            return@signalFlow {
                synchronized(uxSinks) {
                    uxSinks -= sink
                    if (uxSinks.isEmpty()) manager.unregisterListener()
                }
            }
        },
    )
}

/** AAOS VehicleGear 값을 앱의 [Gear]로 바꿉니다. 전진 단수는 모두 D입니다. */
internal fun gearFromVehicleGear(value: Int): Gear = when (value) {
    VehicleGear.GEAR_PARK -> Gear.P
    VehicleGear.GEAR_REVERSE -> Gear.R
    VehicleGear.GEAR_NEUTRAL -> Gear.N
    VehicleGear.GEAR_DRIVE,
    VehicleGear.GEAR_FIRST, VehicleGear.GEAR_SECOND, VehicleGear.GEAR_THIRD,
    VehicleGear.GEAR_FOURTH, VehicleGear.GEAR_FIFTH, VehicleGear.GEAR_SIXTH,
    VehicleGear.GEAR_SEVENTH, VehicleGear.GEAR_EIGHTH, VehicleGear.GEAR_NINTH,
    -> Gear.D
    else -> Gear.UNKNOWN
}
