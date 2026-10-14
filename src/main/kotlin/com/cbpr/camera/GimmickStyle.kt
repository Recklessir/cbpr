package com.cbpr.camera

enum class GimmickStyle(
    val seconds: Double,

    val changeAt: Double,
    val flashRgb: Int,

    val barHoldSeconds: Double = 8.0,
) {
    MEGA(2.2, 0.5, 0xFFFFFF, barHoldSeconds = 12.0),
    TERA(1.8, 0.45, 0x7FE8FF),
    ZPOWER(2.0, 0.6, 0xFFD25A),
    ULTRA(1.9, 0.72, 0xB48CFF),
}
