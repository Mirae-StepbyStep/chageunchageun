package com.mirae.chageunchageun.feature.warning

import com.mirae.chageunchageun.MainDispatcherRule
import com.mirae.chageunchageun.core.model.DrivePhase
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.core.port.WarningSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WarningViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val warnings = MutableStateFlow<Signal<Set<WarningType>>>(Signal.Unavailable("test"))
    private val source = object : WarningSource {
        override val activeWarnings = warnings
    }

    private fun viewModel() = WarningViewModel(source)

    @Test
    fun `위험 경고가 먼저 오고 출처를 함께 준다`() = runTest {
        val vm = viewModel()
        warnings.value = Signal.Value(setOf(WarningType.TIRE_PRESSURE, WarningType.BRAKE_FLUID), Source.SCRIPTED)

        val ui = vm.ui.first { it.guides.isNotEmpty() }
        assertEquals(listOf(WarningType.BRAKE_FLUID, WarningType.TIRE_PRESSURE), ui.guides.map { it.type })
        assertEquals(Source.SCRIPTED, ui.source)
    }

    @Test
    fun `경고가 없음은 값을 받은 상태다`() = runTest {
        val vm = viewModel()
        warnings.value = Signal.Value(emptySet(), Source.SCRIPTED)

        val ui = vm.ui.first { it.available }
        assertTrue(ui.guides.isEmpty())
    }

    @Test
    fun `값을 못 받으면 사용 불가로 둔다`() = runTest {
        val vm = viewModel()

        val ui = vm.ui.first()
        assertFalse(ui.available)
        assertTrue(ui.guides.isEmpty())
    }

    @Test
    fun `주차 브레이크 경고는 주차 중에는 화면에서 뺀다`() = runTest {
        val phase = MutableStateFlow(DrivePhase.PARKED)
        val vm = WarningViewModel(source, phase)
        warnings.value = Signal.Value(setOf(WarningType.PARKING_BRAKE, WarningType.TIRE_PRESSURE), Source.SCRIPTED)

        assertEquals(listOf(WarningType.TIRE_PRESSURE), vm.ui.first { it.guides.isNotEmpty() }.guides.map { it.type })

        phase.value = DrivePhase.DRIVING
        assertEquals(
            listOf(WarningType.TIRE_PRESSURE, WarningType.PARKING_BRAKE),
            vm.ui.first { it.guides.size == 2 }.guides.map { it.type },
        )
    }
}
