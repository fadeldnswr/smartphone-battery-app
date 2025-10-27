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

        // Read battery status
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val isCharging = when(status){
            BatteryManager.BATTERY_STATUS_CHARGING -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING -> true
            else -> false
        }

        // Read battery charging source
        val chargeSource = when(plugged){
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> "NONE"
        }

        // Read battery health code
        val healthCode = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLT"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            else -> "UNKNOWN"
        }

        return com.example.smartphonebatteryprediction.domain.model.BatteryMetrics(
            voltageMv = voltage,
            currentMa = currentMa,
            temperatureC = tempCelcius,
            isCharging = isCharging,
            chargeSource = chargeSource,
            health = health
        )
    }
}