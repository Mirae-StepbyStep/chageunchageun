package com.mirae.chageunchageun.platform.vehicle

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle

/**
 * Pleos Vehicle SDK 연결 하나를 앱 전체가 함께 씁니다. (v1에서 AppGraph에 연결)
 *
 * 문서 기준 주의점:
 * - initialize()가 예외 없이 끝나도 서비스 연결이 확인된 것은 아닙니다. 첫 조회 성공으로 판단합니다.
 * - get·set·capability 결과는 onComplete/onFailed로 비동기 전달됩니다.
 * - 진행 중인 콜백이 끝나기 전에 release()를 부르지 않습니다.
 * - 리스너는 등록한 것과 같은 객체·area로 해제합니다.
 *
 * Pleos 타입(ai.pleos.*)은 platform/vehicle 밖으로 내보내지 않습니다.
 */
class PleosVehicleSession(private val context: Context) {

    private var vehicle: Vehicle? = null

    @Synchronized
    fun vehicle(): Vehicle = vehicle ?: Vehicle(context.applicationContext).also {
        it.initialize()
        vehicle = it
    }

    @Synchronized
    fun release() {
        vehicle?.release()
        vehicle = null
    }
}
