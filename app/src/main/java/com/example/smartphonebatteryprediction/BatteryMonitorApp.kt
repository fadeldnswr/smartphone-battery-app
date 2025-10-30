package com.example.smartphonebatteryprediction

import android.app.Application
import android.util.Log
import com.example.smartphonebatteryprediction.data.remote.SupabaseProvider
import com.example.smartphonebatteryprediction.data.repository.AuthRepository
import com.example.smartphonebatteryprediction.workers.UploadWorker
import io.github.jan.supabase.auth.auth

class BatteryMonitorApp: Application() {
    override fun onCreate() {
        super.onCreate()

        val client = SupabaseProvider.get(applicationContext)
        val user = client.auth.currentUserOrNull()

        if(user != null){
            UploadWorker.schedulePeriodic(applicationContext)
            Log.i("Battery Monitor", "User Logged in, battery manager scheduled")
        } else {
            Log.i("Battery Monitor", "No user logged in, work manager not scheduled")
        }
    }
}