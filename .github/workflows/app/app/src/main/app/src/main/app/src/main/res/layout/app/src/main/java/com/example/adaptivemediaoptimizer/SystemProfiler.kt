package com.example.adaptivemediaoptimizer

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager

data class SystemMetrics(
    val timestamp: Long,
    val batteryLevel: Float,
    val voltageMv: Int,
    val wifiRssi: Int,
    val rxBytesTotal: Long,
    val isSmartModeActive: Boolean
)

class SystemProfiler(private val context: Context) {

    fun captureMetrics(isSmartMode: Boolean): SystemMetrics {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = (level / scale.toFloat()) * 100f
        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val rssi = wifiManager.connectionInfo.rssi

        val uid = android.os.Process.myUid()
        val rxBytes = TrafficStats.getUidRxBytes(uid)

        return SystemMetrics(
            timestamp = System.currentTimeMillis(),
            batteryLevel = batteryPct,
            voltageMv = voltage,
            wifiRssi = rssi,
            rxBytesTotal = rxBytes,
            isSmartModeActive = isSmartMode
        )
    }
}
