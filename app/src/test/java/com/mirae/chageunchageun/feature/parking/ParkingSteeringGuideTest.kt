package com.mirae.chageunchageun.feature.parking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingSteeringGuideTest {

    @Test
    fun turnAmountReadsQuarterTurns() {
        assertEquals("조금", turnAmountText(20f))
        assertEquals("1/4바퀴", turnAmountText(90f))
        assertEquals("반 바퀴", turnAmountText(-180f))
        assertEquals("3/4바퀴", turnAmountText(270f))
        assertEquals("1바퀴", turnAmountText(360f))
        assertEquals("1과 1/4바퀴", turnAmountText(450f))
        assertEquals("1바퀴 반", turnAmountText(540f))
        assertEquals("4분의 1바퀴", spokenTurnAmount(90f))
        assertEquals("4분의 3바퀴", spokenTurnAmount(-270f))
    }

    @Test
    fun valueTexts() {
        assertEquals("가운데", steeringPositionText(10f))
        assertEquals("오른쪽 1/4바퀴 (+90°)", steeringWithDegText(90f))
        assertEquals("오른쪽 3/4바퀴 (+270°)", steeringWithDegText(270f))
        assertEquals("왼쪽 끝까지 (-540°)", steeringWithDegText(-540f))
        assertEquals("오른쪽 반 바퀴 (+180°)", remainingText(180f))
        assertEquals("도달 (+20°)", remainingText(20f))
    }

    @Test
    fun instructionExamplesFromSpec() {
        // 현재 +90°, 목표 +270° → 오른쪽으로 반 바퀴 더.
        val more = steeringInstruction(90f, 270f)
        assertEquals("오른쪽으로 반 바퀴 더 돌리세요.", more.headline)
        assertEquals(180f, more.remainingDeg, 1e-4f)

        assertEquals("핸들을 오른쪽으로 3/4바퀴 돌리세요.", steeringInstruction(0f, 270f).headline)
        assertEquals("오른쪽으로 1/4바퀴 더 돌리세요.", steeringInstruction(180f, 270f).headline)
        assertEquals("목표 조향각에 도달했습니다. 핸들을 유지하세요.", steeringInstruction(250f, 270f).headline)
        assertEquals("핸들을 중앙으로 정렬하세요.", steeringInstruction(270f, 0f).headline)
        assertEquals("이제 핸들을 왼쪽으로 반 바퀴 돌리세요.", steeringInstruction(270f, 90f).headline)
        // 목표 위치 도착: 아직 R이면 P로 바꾸라고, P로 바꾸면 주차 완료.
        val arrived = steeringInstruction(97f, 0f, done = true)
        assertEquals(InstructionKind.ARRIVED_SHIFT_P, arrived.kind)
        assertEquals("주차 위치에 도착했습니다. 브레이크를 밟고 기어를 P로 바꾸세요.", arrived.headline)
        val parked = steeringInstruction(97f, 0f, done = true, inPark = true)
        assertEquals(InstructionKind.PARKED, parked.kind)
        assertEquals("주차를 완료했습니다. 주변을 확인한 뒤 내리세요.", parked.headline)
    }

    @Test
    fun voiceSaysTheSameThingAsTheScreen() {
        val i = steeringInstruction(0f, 180f)
        assertEquals("핸들을 오른쪽으로 반 바퀴 돌리세요.", i.headline)
        assertEquals("핸들을 오른쪽으로 반 바퀴 돌리세요.", i.voiceText)
        assertEquals("오른쪽으로 4분의 1바퀴 더 돌리세요.", steeringInstruction(180f, 270f).voiceText)
        assertEquals("이제 핸들을 중앙으로 정렬하세요. 왼쪽으로 반 바퀴 돌리세요.", steeringInstruction(180f, 0f).voiceText)
        assertEquals(
            "목표 주차 위치에 도착했습니다. 브레이크를 밟고 기어를 P로 바꿔 주세요.",
            steeringInstruction(0f, 0f, done = true).voiceText,
        )
    }

    @Test
    fun leftSideMirrorsDirection() {
        assertEquals("핸들을 왼쪽으로 1바퀴 돌리세요.", steeringInstruction(0f, -360f).headline)
        assertEquals("왼쪽으로 반 바퀴 더 돌리세요.", steeringInstruction(-90f, -270f).headline)
    }

    @Test
    fun voiceKeyIgnoresSmallChangesButNotNewTargets() {
        val a = steeringInstruction(100f, 450f, phase = ParkPhase.TURN_IN)
        val b = steeringInstruction(300f, 450f, phase = ParkPhase.TURN_IN)
        assertTrue(b.remainingDeg < a.remainingDeg)
        assertEquals(a.voiceKey, b.voiceKey)
        // 목표가 10° 흔들리면 그대로, 1/4바퀴 넘게 바뀌면 다른 키.
        assertEquals(a.voiceKey, steeringInstruction(300f, 460f, phase = ParkPhase.TURN_IN).voiceKey)
        assertNotEquals(a.voiceKey, steeringInstruction(300f, 540f, phase = ParkPhase.TURN_IN).voiceKey)
        // 단계가 바뀌면 다른 키.
        assertNotEquals(a.voiceKey, steeringInstruction(100f, 450f, phase = ParkPhase.STRAIGHTEN).voiceKey)
    }

    @Test
    fun gaugeStateAgainstTarget() {
        assertEquals(GaugeState.NO_TARGET, gaugeState(100f, null))
        assertEquals(GaugeState.ON_TARGET, gaugeState(510f, 540f))
        assertEquals(GaugeState.NEAR, gaugeState(420f, 540f))
        assertEquals(GaugeState.OVERSHOOT, gaugeState(-540f, -270f))
        assertEquals(GaugeState.APPROACHING, gaugeState(540f, 0f))
    }

    /* ---------------- 음성 규칙 ---------------- */

    private fun turn(current: Float, target: Float) =
        steeringInstruction(current, target, phase = ParkPhase.TURN_IN)

    @Test
    fun voiceStartsThenSpeaksTheCurrentStep() {
        val p = VoiceGuidePolicy()
        val i = turn(0f, 360f)
        assertEquals(VOICE_START_TEXT, p.next(true, i, 0))
        // 시작 안내가 끝날 때까지 기다립니다.
        assertNull(p.next(true, i, 1000))
        assertNull(p.next(true, i, 3000))
        assertEquals(i.voiceText, p.next(true, i, 4300))
    }

    /** 시작 안내(0ms) 뒤 첫 안내를 4300ms에 말한 상태. */
    private fun primed(first: SteeringInstruction): VoiceGuidePolicy {
        val p = VoiceGuidePolicy()
        assertEquals(VOICE_START_TEXT, p.next(true, first, 0))
        assertNull(p.next(true, first, 1000))
        assertEquals(first.voiceText, p.next(true, first, 4300))
        return p
    }

    @Test
    fun voiceDoesNotRepeatWhileTurning() {
        val p = primed(turn(0f, 450f))
        // "돌리세요" → "더 돌리세요"로 바뀌어도 같은 동작이라 다시 말하지 않습니다.
        var t = 4300L
        listOf(100f, 200f, 300f).forEach { cur ->
            t += 1000
            assertNull(p.next(true, turn(cur, 450f), t))
        }
    }

    @Test
    fun voiceAnnouncesReachAfterSettleAndGap() {
        val p = primed(turn(0f, 450f))
        val reached = turn(440f, 450f)
        // 막 도달한 순간은 기다림(지나쳐 가는 경우 대비), 0.7초 유지되고 2.5초 간격이 지나면 말함.
        assertNull(p.next(true, reached, 6000))
        assertNull(p.next(true, reached, 6500))
        assertEquals(reached.voiceText, p.next(true, reached, 6900))
        assertNull(p.next(true, reached, 7500))
    }

    @Test
    fun doneIsSpokenImmediatelyAndOnce() {
        val p = primed(turn(0f, 450f))
        val arrived = steeringInstruction(0f, 0f, done = true, phase = ParkPhase.DONE)
        assertEquals(arrived.voiceText, p.next(true, arrived, 4400))
        assertNull(p.next(true, arrived, 9000))
        // P로 바꾸면 곧바로 주차 완료를 한 번 말합니다.
        val parked = steeringInstruction(0f, 0f, done = true, phase = ParkPhase.DONE, inPark = true)
        assertEquals(parked.voiceText, p.next(true, parked, 9100))
        assertNull(p.next(true, parked, 15000))
    }

    @Test
    fun turningGuidanceOffResetsVoice() {
        val p = VoiceGuidePolicy()
        p.next(true, turn(0f, 450f), 0)
        assertNull(p.next(false, null, 100))
        assertEquals(VOICE_START_TEXT, p.next(true, turn(0f, 450f), 200))
    }
}
