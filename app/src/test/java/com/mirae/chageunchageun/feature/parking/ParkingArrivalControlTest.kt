package com.mirae.chageunchageun.feature.parking

import com.mirae.chageunchageun.runtime.PortMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingArrivalControlTest {

    @Test
    fun `virtual arrival depends only on the location port being fake`() {
        assertTrue(arrivalControlWorks(PortMode.FAKE))
        assertFalse(arrivalControlWorks(PortMode.REAL))
    }
}
