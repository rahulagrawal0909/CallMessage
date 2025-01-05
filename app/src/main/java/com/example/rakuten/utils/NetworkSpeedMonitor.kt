package com.example.rakuten.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.DecimalFormat
import kotlin.math.roundToInt
@SuppressLint("MissingPermission")

class NetworkSpeedMonitor(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastTxBytes: Long = 0
    private var lastRxBytes: Long = 0
    private var lastUpdateTime: Long = 0

    private val _networkMetrics = MutableLiveData<NetworkMetrics>()
    val networkMetrics: LiveData<NetworkMetrics> = _networkMetrics

    data class NetworkMetrics(
        val downloadSpeed: String = "0 B/s",
        val uploadSpeed: String = "0 B/s",
        val networkType: String = "Unknown",
        val signalStrength: String = "Unknown",
        val latency: Long = 0,
        val isConnected: Boolean = false,
        val networkOperator: String = "Unknown",
        val maxBandwidth: Int = 0
    )

    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateNetworkMetrics(capabilities)
        }

        override fun onLost(network: Network) {
            updateDisconnectedState()
        }
    }

    fun startMonitoring() {
        // Register network callback
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        // Start periodic updates
        startPeriodicUpdates()
    }

    fun stopMonitoring() {
        handler.removeCallbacksAndMessages(null)
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPeriodicUpdates() {
        val updateRunnable = object : Runnable {
            override fun run() {
                calculateNetworkSpeeds()
                handler.postDelayed(this, 1000) // Update every second
            }
        }
        handler.post(updateRunnable)
    }

    private fun calculateNetworkSpeeds() {
        val currentRxBytes = TrafficStats.getTotalRxBytes()
        val currentTxBytes = TrafficStats.getTotalTxBytes()
        val currentTime = System.currentTimeMillis()

        // Calculate speeds if we have previous measurements
        if (lastUpdateTime != 0L) {
            val timeDiff = (currentTime - lastUpdateTime) / 1000.0 // Convert to seconds

            val downloadSpeed = (currentRxBytes - lastRxBytes) / timeDiff
            val uploadSpeed = (currentTxBytes - lastTxBytes) / timeDiff

            val currentMetrics = _networkMetrics.value ?: NetworkMetrics()
            _networkMetrics.postValue(currentMetrics.copy(
                downloadSpeed = formatSpeed(downloadSpeed),
                uploadSpeed = formatSpeed(uploadSpeed)
            ))
        }

        // Update previous values
        lastRxBytes = currentRxBytes
        lastTxBytes = currentTxBytes
        lastUpdateTime = currentTime
    }

    private fun updateNetworkMetrics(capabilities: NetworkCapabilities) {
        val currentMetrics = _networkMetrics.value ?: NetworkMetrics()

        _networkMetrics.postValue(currentMetrics.copy(
            isConnected = true,
            networkType = getNetworkType(capabilities),
            maxBandwidth = capabilities.linkDownstreamBandwidthKbps,
            networkOperator = telephonyManager.networkOperator ?: "Unknown"
        ))

        // Measure latency
        measureLatency()
    }

    private fun updateDisconnectedState() {
        _networkMetrics.postValue(NetworkMetrics(isConnected = false))
    }

    private fun measureLatency() {
        Thread {
            try {
                val startTime = System.currentTimeMillis()
                val runtime = Runtime.getRuntime()
                val process = runtime.exec("/system/bin/ping -c 1 8.8.8.8")
                val exitValue = process.waitFor()
                val latency = if (exitValue == 0) {
                    System.currentTimeMillis() - startTime
                } else {
                    -1
                }

                val currentMetrics = _networkMetrics.value ?: NetworkMetrics()
                _networkMetrics.postValue(currentMetrics.copy(latency = latency))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun getNetworkType(capabilities: NetworkCapabilities): String {
        return when {
            capabilities.hasTransport(TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(TRANSPORT_CELLULAR) -> getCellularGeneration()
            capabilities.hasTransport(TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Unknown"
        }
    }


    private fun getCellularGeneration(): String {
        return  getNetworkType(telephonyManager.dataNetworkType)
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        val df = DecimalFormat("#.##")
        return when {
            bytesPerSecond >= 1_000_000 -> "${df.format(bytesPerSecond / 1_000_000)} MB/s"
            bytesPerSecond >= 1_000 -> "${df.format(bytesPerSecond / 1_000)} KB/s"
            else -> "${bytesPerSecond.roundToInt()} B/s"
        }
    }
}