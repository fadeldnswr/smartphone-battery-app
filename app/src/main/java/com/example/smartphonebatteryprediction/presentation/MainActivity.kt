package com.example.smartphonebatteryprediction.presentation

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.example.smartphonebatteryprediction.R
import com.example.smartphonebatteryprediction.data.remote.SupabaseProvider
import com.example.smartphonebatteryprediction.di.ServiceLocator
import com.example.smartphonebatteryprediction.presentation.ui.DashboardScreen
import com.example.smartphonebatteryprediction.presentation.viewmodel.DashboardViewModel
import com.example.smartphonebatteryprediction.data.repository.AuthRepository
import com.example.smartphonebatteryprediction.domain.model.ForegroundInfo
import com.example.smartphonebatteryprediction.helper.BatteryOptimizationHelper
import com.example.smartphonebatteryprediction.presentation.ui.LoginActivity
import com.example.smartphonebatteryprediction.workers.UploadWorker
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit


class MainActivity: ComponentActivity(){
    private lateinit var vm: DashboardViewModel
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        permission -> val allGranted = permission.values.all { it }
        if(!allGranted){
            Toast.makeText(
                this, "Few permissions needed for device monitoring", Toast.LENGTH_LONG
            ).show()
        }
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ){}

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        setContent {
            MainActivityContent()
        }
    }

    @Composable
    private fun MainActivityContent(){
        var authState = remember { mutableStateOf<AuthState>(AuthState.Checking) }

        LaunchedEffect(Unit) {
            Log.i("MainActivity", "Checking authentication...")
            delay(300)

            val auth = AuthRepository(applicationContext)
            val currentUser = auth.currentUser()

            Log.i("MainActivity", "Current user: ${currentUser?.email ?: "null"}")

            if (currentUser == null) {
                Log.w("MainActivity", "No user found, redirecting to LoginActivity")
                authState.value = AuthState.NotAuthenticated
            } else {
                Log.i("MainActivity", "User authenticated: ${currentUser.email}")
                authState.value = AuthState.Authenticated
            }
        }

        when(authState.value){
            AuthState.Checking -> LoadingScreen()
            AuthState.NotAuthenticated -> {
                LaunchedEffect(Unit) {
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                }
                LoadingScreen()
            }
            AuthState.Authenticated -> AuthenticatedContent()
        }
    }

    @Composable
    private fun LoadingScreen(){
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    private suspend fun uploadNow(context: Context){

        // Define repo to read metrics
        val repo = ServiceLocator.provideRepository(context)
        val battery = repo.readBattery()
        val network = repo.readNetwork()
        val fgApp = repo.readBackgroundApp().appName
        val deviceId = Build.MODEL + "-" +
                (Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown")
        val ts = Instant.now().toString()
        val currentUa: Long? = battery.currentMa?.let { (it * 1000).toLong() }

        val supabase = SupabaseProvider.get(context)
        val user = supabase.auth.currentUserOrNull()
        val userId = user?.id

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

        val sentToApi = runCatching {
            sendToApi(payload.toString())
        }.onFailure { Log.w("Upload Now", "Send to API failed: ${it}") }.isSuccess

    }

    private suspend fun sendToApi(body: String) = withContext(Dispatchers.IO){
        val url = URL("http://192.168.1.8:8000/data-retrieval/raw-metrics")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer 123455678")
        conn.doOutput = true
        conn.outputStream.use {
                os -> os.write(body.toByteArray(Charsets.UTF_8))
        }
        val code = conn.responseCode
        if(code !in 200..299){
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
            Log.w("Upload Now", "API returned $code, body: $err")
            throw IllegalStateException("API returned $code")
        } else {
            val resp = conn.inputStream?.bufferedReader()?.use { it.readText() }
            Log.i("Upload Now", "API OK ($code), resp: $resp")
        }
    }

    @Composable
    private fun AuthenticatedContent(){
        LaunchedEffect(Unit) {
            Log.i("MainActivity", "Initializing authenticated content")
            setupApp()
        }

        if(::vm.isInitialized){
            val ui = vm.ui.collectAsStateWithLifecycle().value
            val scope = rememberCoroutineScope()
            MaterialTheme {
                DashboardScreen(
                    ui = ui,
                    onStart = { vm.startSampling() },
                    onStop = { vm.stopSampling() },
                    onUpload = {
                        scope.launch {
                            uploadNow(applicationContext)
                        }
                    }
                )
            }
        } else {
            LoadingScreen()
        }
    }

    private fun setupApp(){
        // Request one time permissions
        requestNecessaryPermissions()

        // Request usage access
        if (!hasUsageAccess()){
            Toast.makeText(this, "Berikan izin Usage Access untuk melanjutkan", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        // Request battery optimization white list
        requestBatteryOptimizationWhitelist()

        // Viewmodel and UI
        val repo = ServiceLocator.provideRepository(applicationContext)
        vm = ViewModelProvider(this, DashboardViewModel.Factory(repo))[DashboardViewModel::class.java]

        UploadWorker.schedulePeriodic(applicationContext)

        setContent {
            val ui = vm.ui.collectAsStateWithLifecycle().value
            val scope = rememberCoroutineScope()
            DashboardScreen(
                ui = ui,
                onStart = {
                    vm.startSampling()
                },
                onStop = {
                    vm.stopSampling()
                },
                onUpload = {
                    scope.launch {
                        uploadNow(applicationContext)
                        UploadWorker.runOnceNow(applicationContext)
                    }
                }
            )
        }
    }

    private fun requestNecessaryPermissions(){
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permLauncher.launch(perms.toTypedArray())
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestBatteryOptimizationWhitelist() {
        if(!BatteryOptimizationHelper.shouldRequestBatteryOptimization(this)) return
        val intent = BatteryOptimizationHelper.getRequestBatteryOptimizationIntent(this)

        if(intent == null){
            Toast.makeText(
                this,
                "Could not open battery optimization settings on this device",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val pm = packageManager
        val canHandle = intent.resolveActivity(pm) != null
        if(!canHandle){
            Toast.makeText(
                this,
                "Your device does not support battery optimization exception screen",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        batteryOptimizationLauncher.launch(intent)
    }

    private sealed class AuthState {
        object Checking: AuthState()
        object NotAuthenticated: AuthState()
        object Authenticated: AuthState()
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