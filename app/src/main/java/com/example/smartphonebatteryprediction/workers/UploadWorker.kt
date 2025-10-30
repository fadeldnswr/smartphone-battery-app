package com.example.smartphonebatteryprediction.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import com.example.smartphonebatteryprediction.R
import java.net.HttpURLConnection
import java.net.URL

class UploadWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        Log.i("UploadWorker", "doWork() started")
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception){
            Log.w(TAG, "Could not set foreground: ${e.message}")
        }

        // Define repository for metrics logger
        val repo = ServiceLocator.provideRepository(appContext = applicationContext)
        val battery = repo.readBattery()
        val network = repo.readNetwork()
        val fgApp = repo.readBackgroundApp().appName
        val deviceId = Build.MODEL + "-" +
                (Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown")
        val ts = Instant.now().toString()
        val currentUa: Long? = battery.currentMa?.let { (it * 1000).toLong() }

        val supabase = SupabaseProvider.get(applicationContext)
        val user = supabase.auth.currentUserOrNull()

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

        if(tags.contains("${TAG}_once")){
            try {
                setForeground(createForegroundInfo())
            } catch (e: Exception) {
                Log.w(TAG, "Could not set foreground (not critical")
            }
        }

        val sentToApi = runCatching {
            sendToApi(payload.toString())
        }.onFailure { Log.w(TAG, "Send to API failed: ${it.message}") }.isSuccess

        if(!sentToApi){
            val client = SupabaseProvider.get(applicationContext)
            // Checking session first
            val session = client.auth.currentSessionOrNull()
            // Check if session is null
            if(session == null){
                runCatching { client.auth.currentSessionOrNull() }
                Log.w(TAG, "No valid session, skipping upload")
                return Result.retry()
            }
            return try {
                client.postgrest["raw_metrics"].insert(payload)
                Log.i(TAG, "POST raw_metrics -> success")
                Result.success()
            } catch (e: Throwable){
                Log.e(TAG, "POST raw_metrics failed: ${e.message}", e)
                if(runAttemptCount < 5) {
                    Result.retry()
                } else {
                    Result.failure()
                }

            }
        }
        return Result.success()
    }

    private fun sendToApi(body: String){
        val url = URL("http://192.168.1.8:8000/data-retrieval/raw-metrics")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer 123455678")
        conn.doOutput = true
        conn.outputStream.use {
            os -> os.write(body.toByteArray())
        }
        val code = conn.responseCode
        if(code !in 200..299){
            throw IllegalStateException("API returned $code")
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Device Monitoring")
            .setContentText("Syncing device metrics..")
            .setSmallIcon(R.drawable.ic_stats_upload)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Device Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for device data sync"
                setShowBadge(false)
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "UploadWorker"
        private const val UNIQUE_WORK_NAME = "device_upload_periodic"
        private const val CHANNEL_ID = "device_monitoring_channel"
        private const val NOTIFICATION_ID = 1001

        // Run work manager to send data to Supabase
        fun runOnceNow(context: Context){
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val oneTimeWork = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("${TAG}_once")
                .build()
            WorkManager.getInstance(context).enqueue(oneTimeWork)
            Log.i(TAG, "One time upload triggered")
        }

        fun cancelAll(context: Context){
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.i(TAG, "All periodic work upload cancelled")
        }

        // Schedule periodic send to Supabase
        fun schedulePeriodic(context: Context){
            val constraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false).build()
            val periodic = PeriodicWorkRequestBuilder<UploadWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraint).setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .addTag(TAG).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
            Log.i(TAG, "Periodic upload scheduled for 15 minutes")
        }

        fun isScheduled(context: Context, callback: (Boolean) -> Unit){
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(UNIQUE_WORK_NAME)
                .addListener({
                    val workInfos = WorkManager.getInstance(context)
                        .getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
                    val isScheduled = workInfos.any {
                        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                    }
                    callback(isScheduled)
                }, {it.run()})
        }
    }
}

// Helper untuk nullable put
private fun JsonObjectBuilder.putStringN(k: String, v: String?) =
    if (v == null) put(k, JsonNull) else put(k, JsonPrimitive(v))

private fun JsonObjectBuilder.putBooleanN(k: String, v: Boolean?) =
    if (v == null) put(k, JsonNull) else put(k, JsonPrimitive(v))

private fun JsonObjectBuilder.putIntN(k: String, v: Int?) =
    if (v == null) put(k, JsonNull) else put(k, JsonPrimitive(v))

private fun JsonObjectBuilder.putLongN(k: String, v: Long?) =
    if (v == null) put(k, JsonNull) else put(k, JsonPrimitive(v))

private fun JsonObjectBuilder.putDoubleN(k: String, v: Double?) =
    if (v == null) put(k, JsonNull) else put(k, JsonPrimitive(v))