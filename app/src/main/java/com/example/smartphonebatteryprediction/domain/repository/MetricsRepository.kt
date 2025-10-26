package com.example.smartphonebatteryprediction.domain.repository

import com.example.smartphonebatteryprediction.domain.model.BackgroundApp
import com.example.smartphonebatteryprediction.domain.model.BatteryMetrics
import com.example.smartphonebatteryprediction.domain.model.NetworkMetrics

interface MetricsRepository {
    suspend fun readBattery(): BatteryMetrics
    suspend fun readNetwork(): NetworkMetrics
    suspend fun readBackgroundApp(): BackgroundApp
}