package com.mirae.chageunchageun.core.model

enum class Gear { P, R, N, D, UNKNOWN }

enum class TurnSignal { OFF, LEFT, RIGHT, HAZARD }

/** C1이 다루는 경고. 실제 신호 매핑은 platform 어댑터가 정합니다. */
enum class WarningType { TIRE_PRESSURE, ENGINE, BRAKE_FLUID, ENGINE_OIL, SEAT_BELT, AIRBAG, WASHER_FLUID }

data class CarSize(val lengthMm: Int, val widthMm: Int, val heightMm: Int)

data class GeoPoint(val latitude: Double, val longitude: Double, val headingDeg: Float? = null)

/** A1 시트 축. moveXxx는 방향만 받으므로 위치를 보며 STOP합니다. */
enum class SeatAxis { FORE_AFT, HEIGHT, TILT, BACKREST }

enum class MoveDirection { BACKWARD, STOP, FORWARD }

/** 축별 허용 범위. checkXxxCapabilities 결과에서 옵니다. */
data class SeatRange(val min: Int, val max: Int)
