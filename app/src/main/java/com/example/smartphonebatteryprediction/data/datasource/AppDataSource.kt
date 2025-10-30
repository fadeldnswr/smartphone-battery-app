package com.example.smartphonebatteryprediction.data.datasource

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.smartphonebatteryprediction.domain.model.BackgroundApp

class AppDataSource(private val context: Context) {
    fun readBackgroundApp():com.example.smartphonebatteryprediction.domain.model.BackgroundApp {
        val bgApp = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 5 * 60_000L

        // Find usage apps from last foreground item
        val eventApps = bgApp.queryEvents(beginTime, endTime)
        val event = UsageEvents.Event()
        var pkg: String? = null
        var lastTs = -1L

        // Check if the application has next events
        while(eventApps.hasNextEvent()){
            eventApps.getNextEvent(event)
            val isFg = (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) || (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
            if (isFg && event.timeStamp >= lastTs) {
                lastTs = event.timeStamp
                pkg = event.packageName
            }
        }

        if(pkg == null){
            val stats = bgApp.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, beginTime, endTime
            ).orEmpty()
            pkg = stats.maxByOrNull { it.lastTimeUsed }?.packageName
        }

        val appLabel = pkg?.let { pkg ->
            try {
                val pm = context.packageManager
                val ai = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (_: PackageManager.NameNotFoundException){
                pkg
            } catch (_: Exception){
                pkg
            }
        }
        return BackgroundApp(appName = appLabel)
    }
}