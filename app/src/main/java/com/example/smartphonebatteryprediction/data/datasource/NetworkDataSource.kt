package com.example.smartphonebatteryprediction.data.datasource

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.example.smartphonebatteryprediction.domain.model.NetworkMetrics


private fun Context.hasPerm(p: String) =
    ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

private fun Context.radioPermsGranted(): Boolean {
    val fine = hasPerm(android.Manifest.permission.ACCESS_FINE_LOCATION)
    val nearby = if (android.os.Build.VERSION.SDK_INT >= 33)
        hasPerm(android.Manifest.permission.NEARBY_WIFI_DEVICES) else false
    val phone = hasPerm(android.Manifest.permission.READ_PHONE_STATE)
    return fine || nearby || phone
}

class NetworkDataSource(private val context: Context) {
    @RequiresPermission(allOf = [
        android.Manifest.permission.ACCESS_NETWORK_STATE,
        android.Manifest.permission.ACCESS_WIFI_STATE
    ])
    fun readNetData(): NetworkMetrics {
        val nm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = nm.activeNetwork
        val caps = active?.let { nm.getNetworkCapabilities(it) }

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val channelQualityDbm: Int? = when {
            isWifi && (context.hasPerm(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                    (android.os.Build.VERSION.SDK_INT >= 33 && context.hasPerm(android.Manifest.permission.NEARBY_WIFI_DEVICES)))
                -> readWifiRssiDbm(caps)
            isCellular && context.hasPerm(android.Manifest.permission.ACCESS_FINE_LOCATION)
                -> readCellularDbm()
            else -> null
        }
        return com.example.smartphonebatteryprediction.domain.model.NetworkMetrics(
            networkTypes = when{ isWifi -> "WiFi"; isCellular -> "Cellular"; else -> "None" },
            radioRssiDbm = channelQualityDbm,
            rxBytes = TrafficStats.getTotalRxBytes(),
            txBytes = TrafficStats.getTotalTxBytes(),
        )
    }

    // Function to get WiFi status from Device
    @RequiresPermission(Manifest.permission.ACCESS_WIFI_STATE)
    private fun readWifiRssiDbm(caps: NetworkCapabilities?): Int? {
        val wi = caps?.transportInfo as? android.net.wifi.WifiInfo
        wi?.rssi?.validDbmOrNull()?.let{return it}

        @Suppress("DEPRECATION")
        runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            return wm.connectionInfo?.rssi.validDbmOrNull()
        }
        return null
    }

    // Function to get cellular status from device (5G, 4G, GSM)
    @RequiresPermission(anyOf = [
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE
    ])
    private fun readCellularDbm(): Int? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager

        runCatching { tm.signalStrength }.getOrNull()?.cellSignalStrengths?.let {
            list -> var best: Int? = null
            for (cs in list){
                val v: Int? = when(cs){
                    is android.telephony.CellSignalStrengthNr -> {
                        val rsrp = runCatching { cs.ssRsrp }.getOrNull()
                        (rsrp ?: cs.dbm).validDbmOrNull()
                    }
                    is android.telephony.CellSignalStrengthLte -> {
                        val r = runCatching { cs.rsrp }.getOrNull()
                        (r ?: cs.dbm).validDbmOrNull()
                    }
                    is android.telephony.CellSignalStrengthWcdma -> cs.dbm.validDbmOrNull()
                    is android.telephony.CellSignalStrengthTdscdma -> cs.dbm.validDbmOrNull()
                    is android.telephony.CellSignalStrengthGsm -> cs.dbm.validDbmOrNull()
                    is android.telephony.CellSignalStrengthCdma -> cs.dbm.validDbmOrNull()
                    else -> null
                }
                best = pickBetter(best, v)
            }
            if (best != null) return best
        }
        runCatching { tm.allCellInfo }.getOrNull()?.let {
            cells -> var best: Int? = null
            for(ci in cells){
                val v = when (ci){
                    is android.telephony.CellInfoNr -> ci.cellSignalStrength.dbm
                    is android.telephony.CellInfoLte -> ci.cellSignalStrength.dbm
                    is android.telephony.CellInfoWcdma -> ci.cellSignalStrength.dbm
                    is android.telephony.CellInfoGsm -> ci.cellSignalStrength.dbm
                    is android.telephony.CellInfoTdscdma -> ci.cellSignalStrength.dbm
                    else -> null
                }.validDbmOrNull()
                best = pickBetter(best, v)
            }
            if (best != null) return best
        }
        return null
    }

    private fun Int?.validDbmOrNull(min: Int = -150, max: Int = -1): Int? {
        if (this == null) return null
        return if (this in min..max) this else null
    }

    private fun pickBetter(current: Int?, candidate: Int?): Int? =
        when {
            candidate == null -> current
            current == null -> candidate
            else -> if (candidate > current) candidate else current
        }
}