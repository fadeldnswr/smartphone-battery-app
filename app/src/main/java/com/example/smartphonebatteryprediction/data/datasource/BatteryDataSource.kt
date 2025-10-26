package com.example.smartphonebatteryprediction.data.datasource

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryDataSource(private val context: Context) {
    fun readBatteryData():com.example.smartphonebatteryprediction.domain.model.BatteryMetrics{
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Read voltage
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf {
            it > 0
        }
        // Read temperature
        val tempDecimal = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val tempCelcius = tempDecimal?.takeIf { it >= 0 }?.let { it / 10.0 }

        // Read current
        val currentUa = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (currentUa != Long.MIN_VALUE) currentUa / 1000.0 else null
        return com.example.smartphonebatteryprediction.domain.model.BatteryMetrics(
            voltageMv = voltage,
            currentMa = currentMa,
            temperatureC = tempCelcius
        )
    }
}