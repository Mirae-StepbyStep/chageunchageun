package com.mirae.chageunchageun.domain.guidance

import com.mirae.chageunchageun.core.model.Severity
import com.mirae.chageunchageun.core.model.WarningType

/** 경고 하나의 안내 문구. */
data class WarningGuide(
    val type: WarningType,
    val severity: Severity,
    val title: String,
    val meaning: String,
    val driveAction: String,
    val parkedSteps: List<String>,
)

/**
 * C1 경고 카탈로그. 순수 함수라 JVM 테스트로 확인합니다.
 * 심각도와 문구는 초안이며 제출 전에 차량 공식 매뉴얼과 대조합니다.
 * 어떤 심각도에서도 급정지를 지시하지 않습니다.
 */
object WarningGuidance {

    private const val DANGER_ACTION = "급정지하지 말고 안전한 곳으로 이동한 뒤 점검해 주세요."
    private const val CHECK_LATER_ACTION = "급정지하지 말고 다음 휴게소나 목적지에서 점검해 주세요."
    private val DANGER_STEPS = listOf(
        "안전한 곳에 정차하고 비상등을 켜 주세요.",
        "무리해서 계속 운전하지 말고 긴급출동이나 서비스센터에 연락해 주세요.",
        "연기가 나거나 위급하면 차에서 떨어져 119에 연락해 주세요.",
    )

    fun guide(type: WarningType): WarningGuide = when (type) {
        WarningType.TIRE_PRESSURE -> WarningGuide(
            type, Severity.CAUTION, "타이어 공기압 경고", "타이어 공기압이 낮을 수 있어요.", CHECK_LATER_ACTION,
            listOf(
                "타이어가 눈에 띄게 주저앉았는지 확인해 주세요.",
                "차량 매뉴얼의 적정 공기압을 확인해 보충해 주세요.",
                "경고가 계속 켜지면 서비스센터에서 점검받아 주세요.",
            ),
        )
        WarningType.ENGINE -> WarningGuide(
            type, Severity.DANGER, "엔진 경고", "엔진에 이상이 있을 수 있어요.", DANGER_ACTION, DANGER_STEPS,
        )
        WarningType.BRAKE_FLUID -> WarningGuide(
            type, Severity.DANGER, "브레이크액 경고", "브레이크액이 부족할 수 있어요.", DANGER_ACTION, DANGER_STEPS,
        )
        WarningType.ENGINE_OIL -> WarningGuide(
            type, Severity.DANGER, "엔진오일 경고", "엔진오일이 부족할 수 있어요.", DANGER_ACTION, DANGER_STEPS,
        )
        WarningType.SEAT_BELT -> WarningGuide(
            type, Severity.CAUTION, "안전벨트 경고", "안전벨트를 매지 않은 좌석이 있어요.",
            "급정지하지 말고, 안전하게 멈출 수 있을 때 안전벨트를 확인해 주세요.",
            listOf(
                "모든 탑승자의 안전벨트를 확인해 주세요.",
                "모두 맸는데도 경고가 계속 켜지면 서비스센터에서 점검받아 주세요.",
            ),
        )
        WarningType.AIRBAG -> WarningGuide(
            type, Severity.CAUTION, "에어백 경고", "에어백 장치에 이상이 있을 수 있어요.", CHECK_LATER_ACTION,
            listOf("가까운 서비스센터에서 점검받아 주세요."),
        )
        WarningType.WASHER_FLUID -> WarningGuide(
            type, Severity.INFO, "워셔액 부족", "워셔액이 부족할 수 있어요.", "주행을 마친 뒤 보충해 주세요.",
            listOf("워셔액을 보충해 주세요."),
        )
    }

    /** 심각도가 높은 경고부터 정렬합니다. */
    fun ordered(types: Set<WarningType>): List<WarningGuide> =
        types.map(::guide).sortedWith(compareBy({ it.severity }, { it.type }))

    fun eventId(type: WarningType): String = "warning." + type.name.lowercase()

    /** 음성 문장. INFO 경고는 말하지 않으므로 null입니다. */
    fun speech(guide: WarningGuide): String? =
        if (guide.severity == Severity.INFO) {
            null
        } else {
            "${severityLabel(guide.severity)} 경고예요. ${guide.meaning} ${guide.driveAction}"
        }

    fun severityLabel(severity: Severity): String = when (severity) {
        Severity.DANGER -> "위험"
        Severity.CAUTION -> "주의"
        Severity.INFO -> "정보"
    }
}
