package com.example.smartphonebatteryprediction.domain.model

// Battery metrics
data class BatteryMetrics(
    val voltageMv: Int?,
    val currentMa: Double?,
    val temperatureC: Double?
)