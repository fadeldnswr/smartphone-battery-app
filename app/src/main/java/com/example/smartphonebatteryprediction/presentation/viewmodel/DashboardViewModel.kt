package com.example.smartphonebatteryprediction.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartphonebatteryprediction.domain.repository.MetricsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

data class UIMetrics(
    val currentMa: Double? = null,
    val voltageMv: Int? = null,
    val temperatureC: Double? = null,
    val fgApp: String? = null,
    val wifiRssiDbm: Int? = null,
    val dlBps: Double? = null,
    val ulBps: Double? = null
)

class DashboardViewModel(private val repo: MetricsRepository): ViewModel() {
    private val _ui = MutableStateFlow(UIMetrics())
    val ui: StateFlow<UIMetrics> = _ui

    private var samplingJob: Job? = null

    // Throughput metrics
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTs = 0L

    // Define sampling function
    fun startSampling(periodMs: Long = 2000L){
        if(samplingJob?.isActive == true) return
        samplingJob = viewModelScope.launch {
            while (true){
                // Read metrics from repository
                val battery = repo.readBattery()
                val network = repo.readNetwork()
                val bgApp = repo.readBackgroundApp()

                // Read metrics
                val ts = System.currentTimeMillis()
                val dt = (ts - lastTs).takeIf { it > 0 } ?: 0
                val dRx = if (lastTs == 0L) 0 else max(0, network.rxBytes - lastRx)
                val dTx = if (lastTs == 0L) 0 else max(0, network.txBytes - lastTx)
                val dlBps = if (dt > 0) dRx.toDouble() / dt * 1000 else null
                val ulBps = if (dt > 0) dTx.toDouble() / dt * 1000 else null
                lastRx = network.rxBytes; lastTx = network.txBytes; lastTs = ts

                // Access UI metrics values
                _ui.value = UIMetrics(
                    currentMa = battery.currentMa,
                    voltageMv = battery.voltageMv,
                    temperatureC = battery.temperatureC,
                    fgApp = bgApp.appName,
                    wifiRssiDbm = network.radioRssiDbm,
                    dlBps = dlBps,
                    ulBps = ulBps
                )
                delay(periodMs)
            }
        }
    }

    // Define function to stop sampling data
    fun stopSampling(){
        samplingJob?.cancel()
        samplingJob = null
        lastRx = 0L; lastTx = 0L; lastTs = 0L
    }

    // Define factory class
    class Factory(private val repo: MetricsRepository): ViewModelProvider.Factory{
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repo) as T
        }
    }
}