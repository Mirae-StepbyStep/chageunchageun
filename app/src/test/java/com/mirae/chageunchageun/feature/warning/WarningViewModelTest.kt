package com.mirae.chageunchageun.feature.warning

import com.mirae.chageunchageun.MainDispatcherRule
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
}
