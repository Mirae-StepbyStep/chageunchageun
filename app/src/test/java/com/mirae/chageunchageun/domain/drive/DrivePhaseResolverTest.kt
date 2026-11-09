package com.mirae.chageunchageun.domain.drive

import com.mirae.chageunchageun.core.model.DrivePhase
import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.NOT_YET
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivePhaseResolverTest {

    private fun <T> v(value: T): Signal<T> = Signal.Value(value, Source.SCRIPTED)

    @Test
    fun `P면 정차, 그 외 기어는 주행`() {
        assertEquals(DrivePhase.PARKED, DrivePhaseResolver.phase(v(Gear.P)))
        assertEquals(DrivePhase.DRIVING, DrivePhaseResolver.phase(v(Gear.D)))
        assertEquals(DrivePhase.DRIVING, DrivePhaseResolver.phase(v(Gear.R)))
    }

    @Test
    fun `기어를 모르면 주행으로 본다`() {
        assertEquals(DrivePhase.DRIVING, DrivePhaseResolver.phase(NOT_YET))
    }

    @Test
    fun `상세 화면은 정차이고 규제 없음이 확인될 때만`() {
        assertTrue(DrivePhaseResolver.canShowDetail(v(Gear.P), v(false)))
        assertFalse(DrivePhaseResolver.canShowDetail(v(Gear.P), v(true)))
        assertFalse(DrivePhaseResolver.canShowDetail(v(Gear.P), NOT_YET))
        assertFalse(DrivePhaseResolver.canShowDetail(v(Gear.D), v(false)))
    }
}
