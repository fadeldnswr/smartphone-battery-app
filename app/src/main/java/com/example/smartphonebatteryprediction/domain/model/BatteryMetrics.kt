package com.example.smartphonebatteryprediction.domain.model

// Battery metrics
data class BatteryMetrics(
    val voltageMv: Int? = null,
    val currentMa: Double? = null,
    val temperatureC: Double? = null,
    val isCharging: Boolean? = null,
    val chargeSource: String? = null,
    val health: String? = null,
    val batteryLevel: Int? = null,
    val currentAvgUa: Long? = null,
    val cycleCount: Int? = null,
    val chargeCounter: Int? = null,
    val energyCounter: Long? = null,
    val batteryCapacity: Int? = null
)