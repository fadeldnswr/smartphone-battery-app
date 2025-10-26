package com.example.smartphonebatteryprediction.workers

import android.content.Context
import java.util.concurrent.TimeUnit
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.*
import com.example.smartphonebatteryprediction.data.remote.SupabaseClient
import com.example.smartphonebatteryprediction.di.ServiceLocator
import java.time.Instant
import com.example.smartphonebatteryprediction.R

class UploadWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        Log.i("UploadWorker", "doWork() started")
        val url = inputData.getString(SUPABASE_API_URL) ?: applicationContext.getString(R.string.SUPABASE_API_URL)
        val key = inputData.getString(SUPABASE_API_KEY) ?: applicationContext.getString(R.string.SUPABASE_API_KEY)

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
            "rssi_dbm":${network.radioRssiDbm ?: "null"},
            "rx_total_bytes":${network.rxBytes},
            "tx_total_bytes":${network.txBytes},
            "batt_voltage_mv":${battery.voltageMv ?: "null"},
            "batt_current_ua":${battery.currentMa?.let { (it*1000).toLong() } ?: "null"},
            "batt_temp_dc":${battery.temperatureC?.let { (it*10).toInt() } ?: "null"},
            "thermal_status":null,
            "fg_pkg":${if (fgApp != null) "\"$fgApp\"" else "null"}
          }
        """.trimIndent()

        // Insert data to Supabase
        val sendData = SupabaseClient(url, key).insertRawData(json)
        Log.i("UploadWorker", "POST raw_smartphone_data -> success=$sendData")

        // Create for 5 minutes interval
        scheduleNext(applicationContext, url, key)
        Log.i("UploadWorker", "doWork() finished: success=$sendData")
        return if (sendData) Result.success() else Result.retry()
    }

    companion object {
        private val SUPABASE_API_URL = "SUPABASE_API_URL"
        private val SUPABASE_API_KEY = "SUPABASE_API_KEY"

        // Define function to send data every 5 mins
        fun scheduleForFiveMins(context: Context, url: String, key: String){
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInitialDelay(1, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(SUPABASE_API_URL to url, SUPABASE_API_KEY to key))
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
        }
        // Define function to cancel data
        fun cancel(context: Context){
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
        // Define private function to schedule next data
        private fun scheduleNext(context: Context, url: String, key: String) = scheduleForFiveMins(context, url, key)
        private const val UNIQUE = "upload_5min_unique"
        private const val TAG = "upload_5min"
    }
}