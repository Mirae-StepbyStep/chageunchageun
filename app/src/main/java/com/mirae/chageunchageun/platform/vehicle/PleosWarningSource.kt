package com.mirae.chageunchageun.platform.vehicle

import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.core.port.WarningSource
import com.mirae.chageunchageun.platform.reason
import com.mirae.chageunchageun.platform.signalFlow
import kotlinx.coroutines.flow.Flow

/**
 * Pleos Vehicle SDK의 Safety 경고(Telltale) 구독. 담당: 재균
 * 생성자는 AppGraph에 이미 연결돼 있으니 바꾸지 않습니다.
 *
 * 에뮬레이터 v2.1.0에서 확인한 것(docs/c1/01-probe-results.md):
 * - registerTelltales는 등록 직후와 변화가 있을 때마다 "꺼지지 않은 경고등" 목록 전체를 줍니다.
 * - 꺼진 경고등은 목록에서 빠집니다. 모두 꺼지면 빈 목록입니다.
 * - 권한은 pleos.car.permission.PRIVATE_CAR_SAFETY (조회는 CAR_SAFETY).
 */
class PleosWarningSource internal constructor(private val api: TelltaleApi) : WarningSource {

    constructor(session: PleosVehicleSession) : this(PleosTelltaleApi(session))

    override val activeWarnings: Flow<Signal<Set<WarningType>>> = signalFlow(
        source = Source.PLEOS,
        getCurrent = { sink ->
            api.get({ sink.value(warningsFrom(it)) }, { sink.unavailable("getTelltalesInfo failed: ${it.reason()}") })
        },
        register = { sink ->
            val listener = object : TelltaleInfoListener {
                override fun onTelltaleInfoUpdated(telltales: Telltales?) = sink.value(telltales?.let(::warningsFrom))

                override fun onFailed(e: Exception) = sink.unavailable("registerTelltales failed: ${e.reason()}")
            }
            api.register(listener)
            return@signalFlow { api.unregister(listener) } // 등록한 것과 같은 객체로 해제
        },
    )
}

/** 어댑터가 쓰는 Telltale 호출 세 개. 테스트에서 가짜로 바꿉니다. */
internal interface TelltaleApi {
    fun get(onResult: (Telltales) -> Unit, onFailed: (Exception) -> Unit)
    fun register(listener: TelltaleInfoListener)
    fun unregister(listener: TelltaleInfoListener)
}

private class PleosTelltaleApi(private val session: PleosVehicleSession) : TelltaleApi {
    // 처음 수집할 때 Vehicle을 초기화합니다.
    private fun safety() = session.vehicle().getSafety()

    override fun get(onResult: (Telltales) -> Unit, onFailed: (Exception) -> Unit) = safety().getTelltalesInfo(onResult, onFailed)
    override fun register(listener: TelltaleInfoListener) = safety().registerTelltales(listener)
    override fun unregister(listener: TelltaleInfoListener) = safety().unregisterTelltales(listener)
}

/** C1이 안내하는 경고등만 옮깁니다. 여기 없는 경고등(안전벨트, 엔진, 엔진오일 압력 등)은 무시합니다. */
private val WARNING_BY_TELLTALE = mapOf(
    TelltaleType.TPMS to WarningType.TIRE_PRESSURE,
    TelltaleType.PARKING_BRAKE to WarningType.PARKING_BRAKE,
    TelltaleType.ELECTRONIC_PARKING_BRAKE to WarningType.PARKING_BRAKE,
    TelltaleType.MDPS to WarningType.STEERING_ASSIST,
    TelltaleType.ABS to WarningType.ABS,
    TelltaleType.ESC to WarningType.ESC,
    TelltaleType.ESC_OFF to WarningType.ESC_OFF,
    TelltaleType.EV_SYSTEM to WarningType.EV_SYSTEM,
    TelltaleType.POWER_DOWN to WarningType.POWER_DOWN,
    TelltaleType.LOW_VOLT_BATTERY to WarningType.LOW_VOLT_BATTERY,
    TelltaleType.AIRBAG to WarningType.AIRBAG,
)

/** 켜짐과 깜빡임은 모두 켜진 것으로 봅니다. 목록에 없으면 꺼진 것입니다. */
internal fun warningsFrom(telltales: Telltales): Set<WarningType> =
    telltales.telltales
        .filter { it.state != TelltaleState.OFF }
        .mapNotNullTo(mutableSetOf()) { WARNING_BY_TELLTALE[it.type] }
