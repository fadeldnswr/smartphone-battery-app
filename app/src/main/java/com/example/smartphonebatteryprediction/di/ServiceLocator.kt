package com.example.smartphonebatteryprediction.di

import android.content.Context
import com.example.smartphonebatteryprediction.data.datasource.AppDataSource
import com.example.smartphonebatteryprediction.data.datasource.BatteryDataSource
import com.example.smartphonebatteryprediction.data.datasource.NetworkDataSource
import com.example.smartphonebatteryprediction.data.repository.MetricsRepositoryImp
import com.example.smartphonebatteryprediction.domain.repository.MetricsRepository

object ServiceLocator {
    @Volatile private var repo: MetricsRepository? = null
    fun provideRepository(appContext: Context): MetricsRepository {
        return repo?:synchronized(this){
            repo ?: MetricsRepositoryImp(
                BatteryDataSource(appContext),
                NetworkDataSource(appContext),
                AppDataSource(appContext)
            ).also { repo=it }
        }
    }
}