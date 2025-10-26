package com.example.smartphonebatteryprediction.domain.model

// Network metrics
data class NetworkMetrics(
    val networkTypes: String,
    val radioRssiDbm: Int?,
    val rxBytes: Long,
    val txBytes: Long
)