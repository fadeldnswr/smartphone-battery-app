package com.example.smartphonebatteryprediction.data.datasource

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class AppDataSource(private val context: Context) {
    fun readBackgroundApp():com.example.smartphonebatteryprediction.domain.model.BackgroundApp {
        val bgApp = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 5 * 60_000L
        val eventApps = bgApp.queryEvents(beginTime, endTime)
        val event = UsageEvents.Event()
        var pkg: String? = null

        // Check if the application has next events
        while(eventApps.hasNextEvent()){
            eventApps.getNextEvent(event)
            if(event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                pkg = event.packageName
            }
        }
        return com.example.smartphonebatteryprediction.domain.model.BackgroundApp(appName = pkg)
    }
}