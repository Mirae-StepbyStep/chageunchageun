package com.mirae.chageunchageun.feature.home

import com.mirae.chageunchageun.MainDispatcherRule
import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.Lamp
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.data.settings.InMemorySettingsStore
import com.mirae.chageunchageun.data.settings.StoredUserSettings
import com.mirae.chageunchageun.demo.FakeControlsSource
import com.mirae.chageunchageun.demo.FakeDriveStateSource
import com.mirae.chageunchageun.demo.FakeVehicleBus
import com.mirae.chageunchageun.demo.FakeWarningSource
import com.mirae.chageunchageun.domain.settings.AdvisorLevel
import com.mirae.chageunchageun.domain.settings.LocationConsent
import com.mirae.chageunchageun.runtime.DriveRuntime
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val bus = FakeVehicleBus() // starts parked: gear P, no UX restriction
    private val settings = StoredUserSettings(InMemorySettingsStore())

    private fun TestScope.viewModel(withSettings: Boolean = true): HomeViewModel {
        val driveState = FakeDriveStateSource(bus)
        // Unconfined, so the runtime follows the fake bus without advancing the scheduler.
        val runtime = DriveRuntime(driveState, backgroundScope + main.dispatcher)
        val vm = if (withSettings) {
            HomeViewModel(driveState, FakeControlsSource(bus), runtime, FakeWarningSource(bus), settings)
        } else {
            HomeViewModel(driveState, FakeControlsSource(bus), runtime)
        }
        backgroundScope.launch(main.dispatcher) { vm.ui.collect {} }
        return vm
    }

    @Test
    fun `parked shows detail, driving or UX-restricted does not`() = runTest {
        val vm = viewModel()
        assertTrue(vm.ui.value.canShowDetail)

        bus.gear.value = Gear.D
        assertFalse(vm.ui.value.canShowDetail)

        bus.gear.value = Gear.P
        bus.uxRestricted.value = true
        assertFalse(vm.ui.value.canShowDetail)
    }

    @Test
    fun `first-run card follows completion`() = runTest {
        val vm = viewModel()
        assertFalse(vm.ui.value.firstRunCompleted)
        settings.completeFirstRun()
        assertTrue(vm.ui.value.firstRunCompleted)
    }

    @Test
    fun `level and consent follow settings`() = runTest {
        val vm = viewModel()
        assertEquals(AdvisorLevel.DETAILED, vm.ui.value.level)
        settings.setAdvisorLevel(AdvisorLevel.BRIEF)
        settings.setLocationConsent(LocationConsent.GRANTED)
        assertEquals(AdvisorLevel.BRIEF, vm.ui.value.level)
        assertEquals(LocationConsent.GRANTED, vm.ui.value.consent)
    }

    @Test
    fun `location summary has three states`() {
        assertEquals(LocationStatus.OFF, locationStatus(LocationConsent.NOT_ASKED, permissionGranted = true))
        assertEquals(LocationStatus.OFF, locationStatus(LocationConsent.DECLINED, permissionGranted = true))
        assertEquals(LocationStatus.PERMISSION_OFF, locationStatus(LocationConsent.GRANTED, permissionGranted = false))
        assertEquals(LocationStatus.ON, locationStatus(LocationConsent.GRANTED, permissionGranted = true))
    }

    @Test
    fun `no warning means no warning card`() = runTest {
        val vm = viewModel()
        assertNull(vm.ui.value.parkedWarning)
        assertNull(vm.ui.value.drivingWarning)
    }

    @Test
    fun `the most severe warning is shown, parked and driving`() = runTest {
        val vm = viewModel()
        bus.warnings.value = setOf(WarningType.TIRE_PRESSURE, WarningType.ENGINE)
        assertEquals(WarningType.ENGINE, vm.ui.value.parkedWarning?.type)
        assertEquals(2, vm.ui.value.warnings.size)

        bus.gear.value = Gear.D
        assertEquals(WarningType.ENGINE, vm.ui.value.drivingWarning?.type)
    }

    @Test
    fun `an INFO warning is shown parked but not while driving`() = runTest {
        val vm = viewModel()
        bus.warnings.value = setOf(WarningType.WASHER_FLUID)
        assertEquals(WarningType.WASHER_FLUID, vm.ui.value.parkedWarning?.type)

        bus.gear.value = Gear.D
        assertNull(vm.ui.value.drivingWarning)
    }

    @Test
    fun `the parking brake warning is not shown while parked`() = runTest {
        val vm = viewModel()
        bus.warnings.value = setOf(WarningType.PARKING_BRAKE)
        assertNull(vm.ui.value.parkedWarning)
    }

    @Test
    fun `example cards still get their values, with or without the new inputs`() = runTest {
        for (withSettings in listOf(true, false)) {
            val vm = viewModel(withSettings)
            bus.setLamp(Lamp.HIGH_BEAM, LampState.ON)
            assertEquals(Gear.P, vm.ui.value.gear)
            assertEquals(LampState.ON, vm.ui.value.highBeam)
            bus.setLamp(Lamp.HIGH_BEAM, LampState.OFF)
        }
    }

    @Test
    fun `without settings there is no first-run card and no warning`() = runTest {
        val vm = viewModel(withSettings = false)
        bus.warnings.value = setOf(WarningType.ENGINE)
        assertTrue(vm.ui.value.firstRunCompleted)
        assertNull(vm.ui.value.parkedWarning)
    }
}
