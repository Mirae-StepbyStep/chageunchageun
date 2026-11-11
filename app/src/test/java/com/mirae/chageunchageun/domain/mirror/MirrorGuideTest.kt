package com.mirae.chageunchageun.domain.mirror

import com.mirae.chageunchageun.feature.mirror.MirrorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorGuideTest {

    @Test
    fun `안내 순서는 자세, 룸미러, 왼쪽, 오른쪽, 완료`() {
        assertEquals(
            listOf(MirrorStep.POSTURE, MirrorStep.ROOM, MirrorStep.LEFT, MirrorStep.RIGHT, MirrorStep.DONE),
            MirrorGuide.steps,
        )
    }

    @Test
    fun `다음과 이전은 한 단계씩 움직이고 끝에서 멈춘다`() {
        assertEquals(MirrorStep.ROOM, MirrorGuide.next(MirrorStep.POSTURE))
        assertEquals(MirrorStep.DONE, MirrorGuide.next(MirrorStep.DONE))
        assertEquals(MirrorStep.LEFT, MirrorGuide.previous(MirrorStep.RIGHT))
        assertEquals(MirrorStep.POSTURE, MirrorGuide.previous(MirrorStep.POSTURE))
    }

    @Test
    fun `처음과 마지막 단계 판단`() {
        assertTrue(MirrorGuide.isFirst(MirrorStep.POSTURE))
        assertFalse(MirrorGuide.isFirst(MirrorStep.ROOM))
        assertTrue(MirrorGuide.isLast(MirrorStep.DONE))
        assertFalse(MirrorGuide.isLast(MirrorStep.RIGHT))
    }

    @Test
    fun `거울 단계만 움직이는 그림이다`() {
        assertEquals(
            listOf(MirrorStep.ROOM, MirrorStep.LEFT, MirrorStep.RIGHT),
            MirrorGuide.steps.filter(MirrorGuide::isAnimated),
        )
    }

    @Test
    fun `어긋난 상태로 시작해 맞은 상태로 끝난다`() {
        assertEquals(1f, MirrorGuide.misalignment(0f), 0f)
        assertEquals(1f, MirrorGuide.misalignment(MirrorGuide.HOLD_START), 0f)
        assertEquals(0f, MirrorGuide.misalignment(MirrorGuide.MOVE_END), 0f)
        assertEquals(0f, MirrorGuide.misalignment(1f), 0f)
    }

    @Test
    fun `맞춰지는 동안 되돌아가지 않고 범위를 벗어나지 않는다`() {
        var last = 1f
        for (i in 0..100) {
            val m = MirrorGuide.misalignment(i / 100f)
            assertTrue(m in 0f..1f)
            assertTrue(m <= last)
            last = m
        }
        assertEquals(1f, MirrorGuide.misalignment(-1f), 0f)
        assertEquals(0f, MirrorGuide.misalignment(2f), 0f)
    }

    @Test
    fun `안내 문장은 짧게 유지한다`() {
        MirrorGuide.steps.forEach { assertTrue(it.name, it.body.length <= 40) }
    }

    @Test
    fun `화면 상태는 단계 이동을 따라간다`() {
        val vm = MirrorViewModel()
        assertEquals(MirrorStep.POSTURE, vm.ui.value.step)
        assertFalse(vm.ui.value.canGoBack)

        vm.next()
        assertEquals(MirrorStep.ROOM, vm.ui.value.step)
        assertTrue(vm.ui.value.canGoBack)

        repeat(10) { vm.next() }
        assertEquals(MirrorStep.DONE, vm.ui.value.step)
        assertTrue(vm.ui.value.isLast)

        vm.restart()
        assertEquals(MirrorStep.POSTURE, vm.ui.value.step)
    }
}
