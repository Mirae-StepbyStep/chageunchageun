package com.mirae.chageunchageun.domain.guidance

import com.mirae.chageunchageun.core.model.DrivePhase
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
                // TODO(manual-verify)
                "타이어가 주저앉았거나 손상돼 보이면 운전하지 말고 긴급출동에 연락해 주세요.",
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
        // Added 11/12. Every action below is a draft: TODO(manual-verify) against the official owner's manual.
        WarningType.PARKING_BRAKE -> WarningGuide(
            type, Severity.CAUTION, "주차 브레이크 경고", "주차 브레이크가 걸린 채 주행하고 있을 수 있어요.",
            // TODO(manual-verify)
            "급정지하지 말고 안전한 곳에 천천히 멈춘 뒤 주차 브레이크를 풀어 주세요.",
            listOf(
                // TODO(manual-verify)
                "브레이크 페달을 밟은 채 전자식 주차 브레이크 스위치로 해제해 주세요.",
                "해제해도 경고가 계속 켜지면 서비스센터에서 점검받아 주세요.",
            ),
        )
        WarningType.STEERING_ASSIST -> WarningGuide(
            type, Severity.DANGER, "조향 보조 경고",
            "핸들을 가볍게 해 주는 장치에 이상이 있어 핸들이 갑자기 무거워질 수 있어요.",
            // TODO(manual-verify)
            "급정지하지 말고 핸들을 두 손으로 꼭 잡고 속도를 줄여 안전한 곳으로 이동해 주세요.",
            listOf(
                // TODO(manual-verify)
                "안전한 곳에 정차하고 비상등을 켜 주세요.",
                "계속 운전하지 말고 긴급출동이나 서비스센터에 연락해 주세요.",
                "위급한 상황이면 119에 연락해 주세요.",
            ),
        )
        WarningType.ABS -> WarningGuide(
            type, Severity.CAUTION, "ABS 경고",
            "브레이크는 작동하지만 미끄럼을 막아 주는 ABS가 작동하지 않을 수 있어요.",
            // TODO(manual-verify)
            "급정지하지 말고 앞차와 거리를 넉넉히 두고 운전해 주세요.",
            listOf(
                // TODO(manual-verify)
                "점검 전까지는 미끄러운 길에서 특히 천천히 운전해 주세요.",
                "가까운 시일 안에 서비스센터에서 점검받아 주세요.",
            ),
        )
        WarningType.ESC -> WarningGuide(
            type, Severity.CAUTION, "차체 자세 제어(ESC) 경고",
            "미끄러운 길에서 장치가 작동하면 깜빡이고, 계속 켜져 있으면 이상이 있을 수 있어요.",
            // TODO(manual-verify)
            "급정지하지 말고 속도를 줄여 주세요. 젖은 길과 커브에서는 더 천천히 가 주세요.",
            listOf(
                // TODO(manual-verify)
                "깜빡이다 꺼졌다면 장치가 작동한 것이니 미끄러운 길에서 천천히 운전해 주세요.",
                "경고가 계속 켜져 있으면 서비스센터에서 점검받아 주세요.",
            ),
        )
        WarningType.ESC_OFF -> WarningGuide(
            type, Severity.INFO, "차체 자세 제어(ESC) 꺼짐",
            "차체 자세 제어 장치가 꺼져 있어요. 버튼을 실수로 눌렀을 수 있어요.",
            "정차한 뒤 차체 자세 제어 장치를 다시 켜 주세요.",
            listOf(
                // TODO(manual-verify)
                "ESC OFF 버튼을 한 번 눌러 다시 켜 주세요.",
                "눌러도 표시가 꺼지지 않으면 서비스센터에서 점검받아 주세요.",
            ),
        )
        WarningType.EV_SYSTEM -> WarningGuide(
            // TODO(manual-verify): shared danger action and steps
            type, Severity.DANGER, "전기차 시스템 경고", "전기 구동 장치에 이상이 있을 수 있어요.", DANGER_ACTION, DANGER_STEPS,
        )
        WarningType.POWER_DOWN -> WarningGuide(
            type, Severity.CAUTION, "출력 제한 경고", "차의 출력이 제한되어 가속이 약할 수 있어요.",
            // TODO(manual-verify)
            "급정지하지 말고 끼어들기와 추월을 피하고, 다음 안전한 곳에서 점검해 주세요.",
            listOf(
                // TODO(manual-verify)
                "서비스센터에서 점검받아 주세요.",
            ),
        )
        WarningType.LOW_VOLT_BATTERY -> WarningGuide(
            type, Severity.CAUTION, "12V 배터리 경고", "12V 보조 배터리가 충전되지 않고 있을 수 있어요.",
            // TODO(manual-verify)
            "급정지하지 말고 필요 없는 전기 장치를 끄고 서비스센터로 이동해 주세요.",
            listOf(
                // TODO(manual-verify)
                "시동을 끄면 다시 걸리지 않을 수 있어요.",
                "오래 주차하기 전에 서비스센터에서 점검받아 주세요.",
            ),
        )
    }

    /** 주차 브레이크 경고는 주차(P) 중에는 정상이므로 주행 중에만 안내합니다. */
    fun relevant(types: Set<WarningType>, phase: DrivePhase): Set<WarningType> =
        if (phase == DrivePhase.PARKED) types - WarningType.PARKING_BRAKE else types

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
