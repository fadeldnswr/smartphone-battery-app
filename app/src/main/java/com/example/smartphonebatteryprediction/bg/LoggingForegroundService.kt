package com.example.smartphonebatteryprediction.bg

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.smartphonebatteryprediction.data.remote.SupabaseProvider
import com.example.smartphonebatteryprediction.di.ServiceLocator
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.provider.Settings
import com.example.smartphonebatteryprediction.workers.UploadWorker
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import java.time.Instant

class LoggingForegroundService: Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val uploadRunnable = object : Runnable {
        override fun run() {
            scope.launch {
                try {
                    running = true
                } catch (e: Exception) {
                    Log.e("FGS", "Upload failed", e)
                }
            }
            handler.postDelayed(this, 15 * 60 * 1000L)
        }
    }
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notif = NotifHelper.build(this, "Collecting and Synching Metrics..")
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotifHelper.NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NotifHelper.NOTIF_ID, notif)
        }

        handler.post(uploadRunnable)

        scope.launch {
            Log.i("FGS", "Started")
            val repo = ServiceLocator.provideRepository(applicationContext)
            val supabase = SupabaseProvider.get(applicationContext)

            while (running){
                try {
                    if(supabase.auth.currentUserOrNull() == null) {
                        Log.w("FGS", "No sessiong, skipping cycle")
                    } else {
                        val battery = repo.readBattery()
                        val network = repo.readNetwork()
                        val fgApp = repo.readBackgroundApp()
                        val deviceId = Build.MODEL + "-" +
                                (Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "Unknown")
                        val ts = Instant.now().toString()

                        val payload = buildMap<String, Any?> {
                            put("device_id", deviceId)
                            put("ts_utc", ts)
                            put("net_type", network.networkTypes)
                            put("channel_quality", network.radioRssiDbm)
                            put("rx_total_bytes", network.rxBytes)
                            put("tx_total_bytes", network.txBytes)
                            put("batt_voltage_mv", battery.voltageMv)
                            put("batt_current_ua", battery.currentMa)
                            put("batt_temp_c", battery.temperatureC)
                            put("is_charging", battery.isCharging)
                            put("battery_health", battery.health)
                            put("cycles_count", battery.cycleCount)
                            put("battery_level", battery.batteryLevel)
                            put("charge_counter_uah", battery.chargeCounter)
                            put("energy_nwh", battery.energyCounter)
                            put("battery_capacity_pct", battery.batteryCapacity)
                            put("current_avg_ua", battery.currentAvgUa)
                            put("fg_pkg", fgApp)
                        }

                        supabase.postgrest
                            .from("raw_metrics")
                            .insert(payload)
                        Log.i("FGS", "Insert success $ts")
                    }
                } catch (t: Throwable){
                    Log.e("FGS", "Cycle Error", t)
                }
                delay(15 * 60 * 1000L)
            }
        }
    }



    override fun onDestroy() {
        running = false
        scope.cancel()
        Log.i("FGS", "Stopped")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        UploadWorker.schedulePeriodic(applicationContext)
    }
}