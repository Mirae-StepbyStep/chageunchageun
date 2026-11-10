package com.mirae.chageunchageun.platform.car

import android.car.VehicleGear
import com.mirae.chageunchageun.core.model.Gear
import org.junit.Assert.assertEquals
import org.junit.Test

class GearMappingTest {
    @Test
    fun `AAOS 기어 값을 앱 기어로 바꾼다`() {
        assertEquals(Gear.P, gearFromVehicleGear(VehicleGear.GEAR_PARK))
        assertEquals(Gear.R, gearFromVehicleGear(VehicleGear.GEAR_REVERSE))
        assertEquals(Gear.N, gearFromVehicleGear(VehicleGear.GEAR_NEUTRAL))
        assertEquals(Gear.D, gearFromVehicleGear(VehicleGear.GEAR_DRIVE))
        assertEquals(Gear.D, gearFromVehicleGear(VehicleGear.GEAR_THIRD))
        assertEquals(Gear.UNKNOWN, gearFromVehicleGear(VehicleGear.GEAR_UNKNOWN))
        assertEquals(Gear.UNKNOWN, gearFromVehicleGear(-1))
    }
}
