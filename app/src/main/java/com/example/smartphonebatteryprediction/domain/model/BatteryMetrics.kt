package com.example.smartphonebatteryprediction.domain.model

// Battery metrics
data class BatteryMetrics(
    val voltageMv: Int? = null,
    val currentMa: Double? = null,
    val temperatureC: Double? = null,
    val isCharging: Boolean? = null,
    val chargeSource: String? = null,
    val health: String? = null,
)