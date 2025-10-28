package com.example.smartphonebatteryprediction.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import java.util.concurrent.TimeUnit
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.smartphonebatteryprediction.di.ServiceLocator
import java.time.Instant
import com.example.smartphonebatteryprediction.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class UploadWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channelId = "sync_metrics"
        val nm = NotificationManagerCompat.from(applicationContext)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(channelId, "Device Analytics Sync",
                NotificationManager.IMPORTANCE_MIN)
            nm.createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Syncing analytics")
            .setOngoing(false)
            .setSilent(true)
            .build()
        return ForegroundInfo(42, notif,
            if (Build.VERSION.SDK_INT >= 34)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
    }
    override suspend fun doWork(): Result {
        Log.i("UploadWorker", "doWork() started")
        setForeground(getForegroundInfo())

        // Define repository for metrics logger
        val repo = ServiceLocator.provideRepository(appContext = applicationContext)
        val battery = repo.readBattery()
        val network = repo.readNetwork()
        val fgApp = repo.readBackgroundApp().appName
        val deviceId = Build.MODEL + "-" +
                (Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown")
        val ts = Instant.now().toString()
        val currentUa: Long? = battery.currentMa?.let { (it * 1000).toLong() }

        // Helper untuk nullable put
        fun JsonObjectBuilder.putStringN(k: String, v: String?) =
            if (v == null) put(k, JsonNull) else put(k, JsonPrimitive(v))

        fun JsonObjectBuilder.putBooleanN(k: String, v: Boolean?) =
            if (v == null) put(k, JsonNull) else put(k, JsonPrimitive(v))

        fun JsonObjectBuilder.putIntN(k: String, v: Int?) =
            if (v == null) put(k, JsonNull) else put(k, JsonPrimitive(v))

        fun JsonObjectBuilder.putLongN(k: String, v: Long?) =
            if (v == null) put(k, JsonNull) else put(k, JsonPrimitive(v))

        fun JsonObjectBuilder.putDoubleN(k: String, v: Double?) =
            if (v == null) put(k, JsonNull) else put(k, JsonPrimitive(v))

        // Define JSON structure to insert data to Supabase
        val payload = buildJsonObject {
            putStringN("device_id", deviceId)
            putStringN("ts_utc", ts)
            putStringN("net_type", network.networkTypes)
            putIntN("channel_quality", network.radioRssiDbm)
            putLongN("rx_total_bytes", network.rxBytes)
            putLongN("tx_total_bytes", network.txBytes)
            putIntN("batt_voltage_mv", battery.voltageMv)              // Int mV
            putLongN("batt_current_ua", currentUa)                    // Long µA
            putDoubleN("batt_temp_c", battery.temperatureC)           // Double °C
            putBooleanN("is_charging", battery.isCharging)        // Boolean
            putStringN("battery_health", battery.health)              // String
            putIntN("battery_level", battery.batteryLevel)            // 0..100
            putIntN("cycles_count", battery.cycleCount)                // Android 14+
            putIntN("charge_counter_uah", battery.chargeCounter)      // int (uAh)
            putLongN("energy_nwh", battery.energyCounter)     // long (nWh)
            putIntN("battery_capacity_pct", battery.batteryCapacity?.toInt())
            putLongN("current_avg_ua", battery.currentAvgUa)          // long (µA)
            putStringN("charge_source", battery.chargeSource)         // "AC"/"USB"/"WIRELESS"/"NONE"

            putStringN("fg_pkg", fgApp)
        }

        // Check client session before upload
        val client = SupabaseProvider.get(applicationContext)
        val session = client.auth.currentSessionOrNull()
        if (session == null){
            Log.w("UploadWorker", "No valid session, skipping upload")
            return Result.retry()
        }

        return try {
            client.postgrest["raw_metrics"].insert(payload)
            Log.i(TAG, "POST raw_metrics -> success")
            Result.success()
        } catch (e: Throwable){
            Log.e(TAG, "POST raw_metrics failed: ${e.message}", e)
            Result.retry()
        }
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
            val periodic = PeriodicWorkRequestBuilder<UploadWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
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