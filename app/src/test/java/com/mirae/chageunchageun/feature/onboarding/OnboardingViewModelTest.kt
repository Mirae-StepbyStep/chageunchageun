package com.mirae.chageunchageun.feature.onboarding

import com.mirae.chageunchageun.MainDispatcherRule
import com.mirae.chageunchageun.data.settings.InMemorySettingsStore
import com.mirae.chageunchageun.data.settings.StoredUserSettings
import com.mirae.chageunchageun.domain.settings.AdvisorLevel
import com.mirae.chageunchageun.domain.settings.LocationConsent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val settings = StoredUserSettings(InMemorySettingsStore())
    private val canShowDetail = MutableStateFlow(true)

    /** Keeps [OnboardingViewModel.ui] subscribed so `ui.value` is current. */
    private fun TestScope.viewModel(): OnboardingViewModel {
        val vm = OnboardingViewModel(settings, canShowDetail)
        backgroundScope.launch(main.dispatcher) { vm.ui.collect {} }
        return vm
    }

    /** What the screen does when it sees launchPermissionRequest. */
    private fun OnboardingViewModel.answerPermission(granted: Boolean) {
        assertTrue(ui.value.launchPermissionRequest)
        onPermissionRequestLaunched()
        assertFalse(ui.value.launchPermissionRequest)
        onPermissionResult(granted)
    }

    @Test
    fun `steps run in order and completion is saved only when leaving the last step`() = runTest {
        val vm = viewModel()
        assertEquals(OnboardingStep.LOCATION, vm.ui.value.step)
        assertFalse(vm.ui.value.editMode)

        vm.agreeToLocation()
        assertEquals(OnboardingStep.LOCATION, vm.ui.value.step) // waits for the permission result
        vm.answerPermission(granted = true)
        assertEquals(OnboardingStep.LEVEL, vm.ui.value.step)
        assertFalse(vm.ui.value.permissionOff)

        vm.selectLevel(AdvisorLevel.NORMAL)
        vm.next()
        assertEquals(OnboardingStep.DONE, vm.ui.value.step)
        assertFalse(settings.firstRunCompleted.value)

        vm.finish()
        assertTrue(settings.firstRunCompleted.value)
        assertTrue(vm.ui.value.editMode)
        assertEquals(LocationConsent.GRANTED, settings.locationConsent.value)
        assertEquals(AdvisorLevel.NORMAL, settings.advisorLevel.value)
    }

    @Test
    fun `back goes one step and stops at the first`() = runTest {
        val vm = viewModel()
        vm.declineLocation()
        vm.next()
        vm.back()
        assertEquals(OnboardingStep.LEVEL, vm.ui.value.step)
        vm.back()
        assertEquals(OnboardingStep.LOCATION, vm.ui.value.step)
        vm.back()
        assertEquals(OnboardingStep.LOCATION, vm.ui.value.step)
    }

    @Test
    fun `skip path keeps defaults and never asks for the permission`() = runTest {
        val vm = viewModel()
        vm.declineLocation()
        assertFalse(vm.ui.value.launchPermissionRequest)
        vm.next()
        vm.finish()

        assertEquals(LocationConsent.DECLINED, settings.locationConsent.value)
        assertEquals(AdvisorLevel.DETAILED, settings.advisorLevel.value)
        assertTrue(settings.firstRunCompleted.value)
    }

    @Test
    fun `a denied permission continues and is reported, without asking again`() = runTest {
        val vm = viewModel()
        vm.agreeToLocation()
        vm.answerPermission(granted = false)

        assertEquals(OnboardingStep.LEVEL, vm.ui.value.step)
        assertTrue(vm.ui.value.permissionOff)
        assertFalse(vm.ui.value.launchPermissionRequest)
        assertEquals(LocationConsent.GRANTED, settings.locationConsent.value)
    }

    @Test
    fun `finish does nothing before the last step`() = runTest {
        val vm = viewModel()
        vm.finish()
        vm.declineLocation()
        vm.finish()
        assertFalse(settings.firstRunCompleted.value)
        assertEquals(OnboardingStep.LEVEL, vm.ui.value.step)
    }

    @Test
    fun `step and selections are kept across a restricted period`() = runTest {
        val vm = viewModel()
        vm.declineLocation()
        vm.selectLevel(AdvisorLevel.BRIEF)

        canShowDetail.value = false
        assertTrue(vm.ui.value.restricted)
        canShowDetail.value = true

        assertFalse(vm.ui.value.restricted)
        assertEquals(OnboardingStep.LEVEL, vm.ui.value.step)
        assertEquals(AdvisorLevel.BRIEF, vm.ui.value.level)
    }

    @Test
    fun `the permission request waits while restricted`() = runTest {
        val vm = viewModel()
        vm.agreeToLocation()
        canShowDetail.value = false
        assertFalse(vm.ui.value.launchPermissionRequest)

        canShowDetail.value = true
        vm.answerPermission(granted = true)
        assertEquals(OnboardingStep.LEVEL, vm.ui.value.step)
    }

    @Test
    fun `after completion the screen is the editor and saves at once`() = runTest {
        settings.setLocationConsent(LocationConsent.DECLINED)
        settings.completeFirstRun()
        val vm = viewModel()
        assertTrue(vm.ui.value.editMode)

        vm.selectLevel(AdvisorLevel.BRIEF)
        assertEquals(AdvisorLevel.BRIEF, settings.advisorLevel.value)

        vm.agreeToLocation()
        vm.answerPermission(granted = false)
        assertEquals(LocationConsent.GRANTED, settings.locationConsent.value)
        assertTrue(vm.ui.value.permissionOff)
        assertEquals(OnboardingStep.LOCATION, vm.ui.value.step) // no wizard step change in edit mode

        vm.declineLocation()
        assertEquals(LocationConsent.DECLINED, settings.locationConsent.value)
        assertFalse(vm.ui.value.permissionOff)
    }

    @Test
    fun `reset returns to a fresh first run`() = runTest {
        val vm = viewModel()
        vm.declineLocation()
        vm.selectLevel(AdvisorLevel.BRIEF)
        vm.next()
        vm.finish()

        vm.resetFirstRun()

        assertFalse(vm.ui.value.editMode)
        assertEquals(OnboardingStep.LOCATION, vm.ui.value.step)
        assertEquals(AdvisorLevel.DETAILED, vm.ui.value.level)
        assertEquals(LocationConsent.NOT_ASKED, vm.ui.value.consent)
    }
}
