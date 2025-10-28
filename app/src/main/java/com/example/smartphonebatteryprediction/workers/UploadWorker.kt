package com.example.smartphonebatteryprediction.workers

import android.content.Context
import java.util.concurrent.TimeUnit
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.*
import com.example.smartphonebatteryprediction.di.ServiceLocator
import java.time.Instant
import com.example.smartphonebatteryprediction.R
import com.example.smartphonebatteryprediction.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import java.time.Period

class UploadWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        Log.i("UploadWorker", "doWork() started")
        val supabase = SupabaseProvider.get(applicationContext)

        val hasRadio = (applicationContext as Context).let {
            val fine = androidx.core.content.ContextCompat.checkSelfPermission(
                it, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val nearby = if (android.os.Build.VERSION.SDK_INT >= 33)
                androidx.core.content.ContextCompat.checkSelfPermission(
                    it, android.Manifest.permission.NEARBY_WIFI_DEVICES
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED else false
            fine || nearby
        }

        // Define repository for metrics logger
        val repo = ServiceLocator.provideRepository(appContext = applicationContext)
        val battery = repo.readBattery()
        val network = repo.readNetwork()
        val fgApp = repo.readBackgroundApp().appName
        val deviceId = Build.MODEL + "-" +
                (Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown")
        val ts = Instant.now().toString()

        // Define JSON structure to insert data to Supabase
        val json = """
          {
            "device_id":"$deviceId",
            "ts_utc":"$ts",
            "net_type":"${network.networkTypes}",
            "channel_quality":${network.radioRssiDbm ?: "null"},
            "rx_total_bytes":${network.rxBytes},
            "tx_total_bytes":${network.txBytes},
            "batt_voltage_mv":${battery.voltageMv ?: "null"},
            "batt_current_ma":${battery.currentMa ?: "null"},
            "batt_temp_c":${battery.temperatureC ?: "null"},
            "charging_status": ${battery.isCharging ?: "null"},
            "battery_health": ${battery.health ?: "null"},
            "cycles_count": ${battery.cycleCount ?: "null"},
            "battery_level": ${battery.batteryLevel ?: "null"},
            "charge_counter_uah": ${battery.chargeCounter ?: "null"},
            "energy_nwh": ${battery.energyCounter ?: "null"},
            "battery_capacity_pct": ${battery.batteryCapacity ?: "null"},
            "current_avg_ua"" ${battery.currentAvgUa ?: "null"},
            ""
            "fg_pkg":${if (fgApp != null) "\"$fgApp\"" else "null"}
          }
        """.trimIndent()

        // Insert data to Supabase
        val sendData = supabase.postgrest["raw_metrics"].insert(json)
        Log.i("UploadWorker", "POST raw_smartphone_data -> success=$sendData")

        // Create for 5 minutes interval
        runOnceNow(applicationContext)
        Log.i("UploadWorker", "doWork() finished: success=$sendData")
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "upload_periodic_unique"
        private const val TAG = "upload_periodic"

        // Run work manager to send data to Supabase
        fun runOnceNow(context: Context){
            val once = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build()
                ).addTag("$TAG-once").build()
            WorkManager.getInstance(context).enqueue(once)
        }

        // Schedule periodic send to Supabase
        fun schedulePeriodic(context: Context){
            val constraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()
            val periodic = PeriodicWorkRequestBuilder<UploadWorker>(10, TimeUnit.MINUTES)
                .setConstraints(constraint).setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic
            )
        }

        // Define function to cancel periodic schedule
        fun cancel(context: Context){
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}