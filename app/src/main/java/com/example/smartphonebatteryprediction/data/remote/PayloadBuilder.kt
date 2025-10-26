package com.example.smartphonebatteryprediction.data.remote

import android.os.Build
import com.example.smartphonebatteryprediction.domain.model.BatteryMetrics
import com.example.smartphonebatteryprediction.domain.model.NetworkMetrics
import android.provider.Settings
import java.time.Instant

object PayloadBuilder {
    // Define function to build JSON
    fun buildJson(
        appContextPackage: String,
        battery: BatteryMetrics,
        network: NetworkMetrics,
        fgPkg: String?
    ): String {
        val deviceId = Build.MODEL + "-" + (Settings.Secure.getString(
            appContextPackage.let { null }, // Placeholder
            Settings.Secure.ANDROID_ID
        )?: "Unknown")

        // Send deviceId from worker
        val ts = Instant.now().toString()

        // Build JSON
        return """
          "device_id": "$deviceId",
          "ts_utc": "$ts",
          "net_type": "${network.networkTypes}",
          "rssi_dbm": ${network.radioRssiDbm ?: "null"},
          "rx_total_bytes": ${network.rxBytes},
          "tx_total_bytes": ${network.txBytes},
          "batt_voltage_mv": ${battery.voltageMv ?: "null"},
          "batt_current_ua": ${battery.currentMa?.let { (it * 1000).toLong() } ?: "null"},
          "batt_temp_dc": ${battery.temperatureC?.let { (it * 10).toInt() } ?: "null"},
          "thermal_status": null,
          "fg_pkg": ${if (fgPkg != null) "\"$fgPkg\"" else "null"}
        """.trimIndent()
    }
}