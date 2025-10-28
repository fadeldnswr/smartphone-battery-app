package com.example.smartphonebatteryprediction.presentation

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartphonebatteryprediction.di.ServiceLocator
import com.example.smartphonebatteryprediction.presentation.ui.DashboardScreen
import com.example.smartphonebatteryprediction.presentation.viewmodel.DashboardViewModel
import com.example.smartphonebatteryprediction.workers.UploadWorker
import com.example.smartphonebatteryprediction.data.repository.AuthRepository
import com.example.smartphonebatteryprediction.presentation.ui.LoginActivity


class MainActivity: ComponentActivity(){
    private lateinit var vm: DashboardViewModel
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        // Auth guarding
        val auth = AuthRepository(applicationContext)
        if(auth.currentUser() == null){
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Request runtime permissions
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= 33){
            perms += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        permLauncher.launch(perms.toTypedArray())

        // Request usage access
        if (!hasUsageAccess()){
            Toast.makeText(this, "Berikan izin Usage Access untuk melanjutkan", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        // Viewmodel and UI
        val repo = ServiceLocator.provideRepository(applicationContext)
        vm = ViewModelProvider(this, DashboardViewModel.Factory(repo))
            .get(DashboardViewModel::class.java)

        setContent {
            val ui = vm.ui.collectAsStateWithLifecycle().value
            DashboardScreen(
                ui = ui,
                onStart = {
                    vm.startSampling()
                    // Send once and set 15 minutes interval
                    UploadWorker.runOnceNow(applicationContext)
                    UploadWorker.schedulePeriodic(applicationContext)
                },
                onStop = {
                    vm.stopSampling()
                    UploadWorker.cancel(applicationContext)
                }
            )
        }
    }
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val op = if (Build.VERSION.SDK_INT >= 29)
            AppOpsManager.OPSTR_GET_USAGE_STATS else "android:get_usage_stats"
        val mode = appOps.checkOpNoThrow(op, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
