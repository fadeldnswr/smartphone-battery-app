package com.example.smartphonebatteryprediction.data.datasource

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager

class NetworkDataSource(private val context: Context) {
    fun readNetData():com.example.smartphonebatteryprediction.domain.model.NetworkMetrics {
        val nm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = nm.activeNetwork
        val caps = active?.let { nm.getNetworkCapabilities(it) }
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val wifiRssi = if (isWifi) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.connectionInfo?.rssi
        } else null
        return com.example.smartphonebatteryprediction.domain.model.NetworkMetrics(
            networkTypes = when{ isWifi -> "WiFi"; isCellular -> "Cellular"; else -> "None" },
            radioRssiDbm = wifiRssi,
            rxBytes = TrafficStats.getTotalRxBytes(),
            txBytes = TrafficStats.getTotalTxBytes(),
        )
    }
}