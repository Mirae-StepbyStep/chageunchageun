package com.example.chaguenchaguen.core.model

/** 앱이 판단한 주행 상태. 신호를 모르면 DRIVING으로 봅니다(안전한 쪽). */
enum class DrivePhase { PARKED, DRIVING }

/** C1 경고 심각도. 일반 안내는 null이며 E1 축소 대상입니다. */
enum class Severity { DANGER, CAUTION, INFO }

/**
 * 감지기(domain)가 만드는 상황. 안내 문구는 [id]로 카탈로그에서 찾습니다.
 * 예: "warning.tire_pressure", "parking.arrived", "trip.ended"
 */
data class SituationEvent(
    val id: String,
    val severity: Severity? = null,
    val facts: Map<String, String> = emptyMap(),
    val atMillis: Long = System.currentTimeMillis(),
)

/** 음성 우선순위. 높은 쪽이 재생 중인 안내를 끊습니다. */
enum class SpeechPriority { NORMAL, WARNING }

/** STT 결과. [isFinal]이 true일 때만 질문으로 처리합니다. */
data class SpeechResult(val text: String, val isFinal: Boolean, val source: Source)
