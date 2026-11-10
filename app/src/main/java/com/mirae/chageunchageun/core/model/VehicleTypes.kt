package com.mirae.chageunchageun.core.model

enum class Gear { P, R, N, D, UNKNOWN }

/** A5가 다루는 외부 등화. */
enum class Lamp { HIGH_BEAM, LOW_BEAM, TAIL, FRONT_FOG, REAR_FOG }

enum class LampState { OFF, ON, DAYTIME_RUNNING }

/** 와이퍼의 실제 동작 상태. 정비 모드 등 그 밖의 상태는 [OTHER]입니다. */
enum class WiperState { OFF, ON, OTHER }

/** C1이 다루는 경고. 실제 신호 매핑은 platform 어댑터가 정합니다. */
enum class WarningType { TIRE_PRESSURE, ENGINE, BRAKE_FLUID, ENGINE_OIL, SEAT_BELT, AIRBAG, WASHER_FLUID }

data class CarSize(val lengthMm: Int, val widthMm: Int, val heightMm: Int)

data class GeoPoint(val latitude: Double, val longitude: Double, val headingDeg: Float? = null)
