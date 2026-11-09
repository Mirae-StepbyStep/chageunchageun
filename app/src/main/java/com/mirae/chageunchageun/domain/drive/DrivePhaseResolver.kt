package com.mirae.chageunchageun.domain.drive

import com.mirae.chageunchageun.core.model.DrivePhase
import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.valueOrNull

/**
 * 주행 상태 판단 규칙. 순수 함수라 JVM 테스트로 확인합니다.
 *
 * - 기어가 P로 확인될 때만 PARKED. 기어를 모르면 DRIVING(안전한 쪽).
 * - 상세 화면은 PARKED이고 주행 규제가 "없음"으로 확인될 때만 엽니다.
 */
object DrivePhaseResolver {

    fun phase(gear: Signal<Gear>): DrivePhase =
        if (gear.valueOrNull() == Gear.P) DrivePhase.PARKED else DrivePhase.DRIVING

    fun canShowDetail(gear: Signal<Gear>, uxRestricted: Signal<Boolean>): Boolean =
        phase(gear) == DrivePhase.PARKED && uxRestricted.valueOrNull() == false
}
