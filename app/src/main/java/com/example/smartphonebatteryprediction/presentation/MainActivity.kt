package com.example.smartphonebatteryprediction.presentation

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
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
import com.example.smartphonebatteryprediction.R


class MainActivity: ComponentActivity(){
    private lateinit var vm: DashboardViewModel
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        val apiUrl = getString(R.string.SUPABASE_API_URL)
        val apiKey = getString(R.string.SUPABASE_API_KEY)

        // Gain minimum access
        permLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        ))

        // Check if user has usage access
        if(!hasUsageAccess()){
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            Toast.makeText(this, "Provide User Access!", Toast.LENGTH_LONG).show()
        }

        // Define repository
        val repo = ServiceLocator.provideRepository(applicationContext)
        vm = ViewModelProvider(this, DashboardViewModel.Factory(repo))
            .get(DashboardViewModel::class.java)
        setContent {
            val ui = vm.ui.collectAsStateWithLifecycle().value
            DashboardScreen(
                ui=ui,
                onStart = {
                    vm.startSampling()
                    UploadWorker.scheduleForFiveMins(
                        applicationContext,
                        url = apiUrl,
                        key = apiKey
                    )
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
        val mode = appOps.checkOpNoThrow("android:get_usage_stats",
                android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
