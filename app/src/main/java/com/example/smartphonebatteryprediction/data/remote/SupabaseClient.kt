package com.example.smartphonebatteryprediction.data.remote

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import com.example.smartphonebatteryprediction.R
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseProvider {
    @Volatile private var instance: SupabaseClient? = null

    fun get(context: Context): SupabaseClient {
        return instance ?: synchronized(this) {
            instance ?: createSupabaseClient(
                supabaseUrl = context.getString(R.string.SUPABASE_API_URL),
                supabaseKey = context.getString(R.string.SUPABASE_API_KEY)
            ) {
                httpEngine = io.ktor.client.engine.okhttp.OkHttp.create()
                install(Postgrest){ defaultSchema = "public" }
                install(Auth) {
                    alwaysAutoRefresh = true
                    autoLoadFromStorage = true
                    autoSaveToStorage = true
                }
                install(Realtime)
            }.also {
                instance = it
            }
        }
    }

    fun reset(){
        synchronized(this){
            instance = null
        }
    }
}