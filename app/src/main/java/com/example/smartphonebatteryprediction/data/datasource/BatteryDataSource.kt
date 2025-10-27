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
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

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

        // Read battery level property
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val levelPct = if(level != null && scale != null && level > 0 && scale > 0)
            (level * 100 / scale) else null

        // Read average current
        val currentAvgUa = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            .takeIf { it != Long.MIN_VALUE }

        // Read cycle count for Android 14+
        val cyclesCount = if (android.os.Build.VERSION.SDK_INT >= 34) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else null

        // Read battery charge counter
        val chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it != Int.MIN_VALUE }

        // Read battery energy counter
        val energyNwh = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            .takeIf { it != Long.MIN_VALUE }

        // Read battery capacity
        val capacityPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }


        return com.example.smartphonebatteryprediction.domain.model.BatteryMetrics(
            voltageMv = voltage,
            currentMa = currentMa,
            temperatureC = tempCelcius,
            isCharging = isCharging,
            chargeSource = chargeSource,
            health = health,
            cycleCount = cyclesCount,
            batteryLevel = levelPct,
            currentAvgUa = currentAvgUa,
            chargeCounter = chargeCounterUah,
            energyCounter = energyNwh,
            batteryCapacity = capacityPct
        )
    }
}