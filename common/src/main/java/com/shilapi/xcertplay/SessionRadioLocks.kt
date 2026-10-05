package com.shilapi.xcertplay

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * Keeps the Wi-Fi radio out of power save and the CPU awake for a running CarPlay session. Without
 * them tablets batch Wi-Fi Direct frames on beacon intervals, which arrives as audio bursts and gaps.
 */
class SessionRadioLocks(context: Context) {
    private val appContext = context.applicationContext
    private var lowLatency: WifiManager.WifiLock? = null
    private var highPerf: WifiManager.WifiLock? = null
    private var wake: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire() {
        if (wake != null) return
        val wifi = appContext.getSystemService(WifiManager::class.java)
        if (wifi != null) {
            if (Build.VERSION.SDK_INT >= 29) {
                lowLatency = runCatching {
                    wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "$TAG:low-latency")
                        .apply { setReferenceCounted(false); acquire() }
                }.onFailure { Log.w(TAG, "low-latency Wi-Fi lock unavailable", it) }.getOrNull()
            }
            highPerf = runCatching {
                @Suppress("DEPRECATION")
                wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$TAG:high-perf")
                    .apply { setReferenceCounted(false); acquire() }
            }.onFailure { Log.w(TAG, "high-performance Wi-Fi lock unavailable", it) }.getOrNull()
        }
        wake = runCatching {
            appContext.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DiPlay:session")
                ?.apply { setReferenceCounted(false); acquireUntimed() }
        }.onFailure { Log.w(TAG, "partial wake lock unavailable", it) }.getOrNull()
        Log.i(TAG, "acquired lowLatency=${lowLatency?.isHeld} highPerf=${highPerf?.isHeld} wake=${wake?.isHeld}")
    }

    @Synchronized
    fun release() {
        listOf(lowLatency, highPerf).forEach { lock -> runCatching { if (lock?.isHeld == true) lock.release() } }
        runCatching { if (wake?.isHeld == true) wake?.release() }
        if (lowLatency != null || highPerf != null || wake != null) Log.i(TAG, "released")
        lowLatency = null
        highPerf = null
        wake = null
    }

    @SuppressLint("WakelockTimeout")
    private fun PowerManager.WakeLock.acquireUntimed() = acquire()

    private companion object {
        const val TAG = "DiPlay-RadioLocks"
    }
}
