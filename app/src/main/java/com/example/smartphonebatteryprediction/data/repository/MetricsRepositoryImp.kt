package com.example.smartphonebatteryprediction.data.repository

import com.example.smartphonebatteryprediction.data.datasource.AppDataSource
import com.example.smartphonebatteryprediction.data.datasource.BatteryDataSource
import com.example.smartphonebatteryprediction.data.datasource.NetworkDataSource
import com.example.smartphonebatteryprediction.domain.model.BatteryMetrics
import com.example.smartphonebatteryprediction.domain.repository.MetricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MetricsRepositoryImp(
    private val battery: BatteryDataSource,
    private val network: NetworkDataSource,
    private val bgApp: AppDataSource)
    : MetricsRepository {
    override suspend fun readBattery() = withContext(Dispatchers.Default){
        battery.readBatteryData()
    }
    override suspend fun readNetwork() = withContext(Dispatchers.Default){
        network.readNetData()
    }
    override suspend fun readBackgroundApp() = withContext(Dispatchers.Default){
        bgApp.readBackgroundApp()
    }
}