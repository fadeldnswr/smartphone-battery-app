package com.example.smartphonebatteryprediction.helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {
    // Check if application ignored battery optimization
    fun isIgnoringBatteryOptimization(context: Context): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) return true

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    // Request permission to disable battery optimization
    fun getRequestBatteryOptimizationIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) return null

        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("Package:${context.packageName}")
        }
    }

    // Check if we should request Battery Optimization
    fun shouldRequestBatteryOptimization(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimization(context)
    }

}