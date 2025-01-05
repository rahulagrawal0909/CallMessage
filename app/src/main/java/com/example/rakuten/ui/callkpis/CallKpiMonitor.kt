//package com.example.rakuten.ui.callkpis
//
//import android.Manifest
//import android.content.Context
//import android.os.Handler
//import android.os.Looper
//import android.telephony.*
//import java.util.concurrent.Executor
//
//class CallKpiMonitor(private val context: Context) {
//    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//    private val mainExecutor = Executor { runnable -> Handler(Looper.getMainLooper()).post(runnable) }
//    private var callback: CallKpiCallback? = null
//    private var callStartTime: Long = 0
//    private var lastSignalStrength: SignalStrength? = null
//    private var callHistory = mutableListOf<CallMetrics>()
//
//    data class CallKpiData(
//        // Signal Quality Metrics
//        var signalStrengthDbm: Int = 0,
//        var signalNoiseRatio: Float = 0f,
//        var rsrp: Int = 0,
//        var rsrq: Float = 0f,
//        var cqi: Int = 0,
//
//        // Voice Quality Metrics
//        var estimatedMos: Float = 0f,
//        var jitter: Float = 0f,
//        var packetLoss: Float = 0f,
//        var voiceClarityIndex: Int = 0,
//        var echoLevel: Float = 0f,
//
//        // Call Setup Metrics
//        var callSetupTime: Long = 0,
//        var callSetupSuccessRate: Float = 0f,
//        var setupFailureRate: Float = 0f,
//        var postDialDelay: Long = 0,
//        var answerSeizureRatio: Float = 0f,
//        var networkResponseTime: Long = 0,
//
//        // Call Stability Metrics
//        var dropCallRate: Float = 0f,
//        var callCompletionRate: Float = 0f,
//        var callDuration: Long = 0,
//        var callRetentionRate: Float = 0f,
//        var handoverSuccessRate: Float = 0f,
//        var networkSwitchingTime: Long = 0,
//        var callContinuityIndex: Float = 0f,
//
//        // Network Performance
//        var networkAvailability: Boolean = false,
//        var networkCongestionLevel: Int = 0,
//        var codecType: String = "",
//        var bandwidthUtilization: Float = 0f,
//        var latencyRtt: Long = 0,
//        var networkType: String = "",
//        var voltePerformanceRating: Int = 0,
//
//        // End User Experience
//        var audioQualityScore: Int = 0,
//        var oneWayAudioCount: Int = 0,
//        var noAudioCount: Int = 0,
//        var echoComplaints: Int = 0,
//        var backgroundNoiseLevel: Float = 0f,
//        var voiceClippingCount: Int = 0,
//        var setupDelayPerception: Long = 0
//    )
//
//    private data class CallMetrics(
//        val duration: Long,
//        val wasDropped: Boolean,
//        val setupTime: Long
//    )
//
//    interface CallKpiCallback {
//        fun onKpiUpdated(kpiData: CallKpiData)
//    }
//
//    private val kpiData = CallKpiData()
//
//    private inner class CallKpiTelephonyCallback : TelephonyCallback(),
//        TelephonyCallback.SignalStrengthsListener,
//        TelephonyCallback.CallStateListener,
//        TelephonyCallback.DisplayInfoListener{
//
//        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
//            lastSignalStrength = signalStrength
//            updateSignalMetrics(signalStrength)
//        }
//
//        override fun onCallStateChanged(state: Int) {
//            when (state) {
//                TelephonyManager.CALL_STATE_RINGING -> {
//                    callStartTime = System.currentTimeMillis()
//                    kpiData.setupDelayPerception = 0
//                }
//                TelephonyManager.CALL_STATE_OFFHOOK -> {
//                    if (callStartTime > 0) {
//                        kpiData.callSetupTime = System.currentTimeMillis() - callStartTime
//                    }
//                    startCallDurationTimer()
//                }
//                TelephonyManager.CALL_STATE_IDLE -> {
//                    if (callStartTime > 0) {
//                        handleCallEnd()
//                    }
//                }
//            }
//            updateKpis()
//        }
//
//        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
//            kpiData.networkType = getNetworkTypeString(info.networkType)
//            updateKpis()
//        }
//
////        override fun onCallQualityChanged(callQuality: CallQuality) {
////            updateCallQualityMetrics(callQuality)
////        }
//    }
//
//    private val telephonyCallback = CallKpiTelephonyCallback()
//
//    fun startMonitoring(callback: CallKpiCallback) {
//        this.callback = callback
//        if (checkPermissions()) {
//            telephonyManager.registerTelephonyCallback(mainExecutor, telephonyCallback)
//            initializeBaseMetrics()
//        }
//    }
//
//    fun stopMonitoring() {
//        telephonyManager.unregisterTelephonyCallback(telephonyCallback)
//        callback = null
//    }
//
//    private fun updateSignalMetrics(signalStrength: SignalStrength) {
//        kpiData.apply {
//            signalStrengthDbm = signalStrength.getCellSignalStrengths()
//                .firstOrNull()?.dbm ?: 0
//
//            if (signalStrength is CellSignalStrength) {
//                rsrp = signalStrength.gsmBitErrorRate
//                rsrq = signalStrength.rsrq.toFloat()
//                signalNoiseRatio = calculateSnr(signalStrength)
//                cqi = signalStrength.cqi
//            }
//        }
//        updateKpis()
//    }
//
//    private fun updateCallQualityMetrics(callQuality: CallQuality) {
//        kpiData.apply {
//            estimatedMos = calculateMos(callQuality)
//            jitter = callQuality.jitterMillis.toFloat()
//            packetLoss = callQuality.packetLossPercent
//            voiceClarityIndex = calculateVoiceClarityIndex(callQuality)
//        }
//        updateKpis()
//    }
//
//    private fun calculateSnr(lteSignal: CellSignalStrengthLte): Float {
//        // Simplified SNR calculation
//        return (lteSignal.rsrp - (-120)).toFloat() // -120 dBm as noise floor
//    }
//
//    private fun calculateMos(callQuality: CallQuality): Float {
//        // Simplified MOS calculation based on multiple factors
//        val jitterFactor = 1 - (callQuality.jitterMillis / 100f).coerceAtMost(1f)
//        val packetLossFactor = 1 - (callQuality.packetLossPercent / 100f)
//        return (jitterFactor * packetLossFactor * 5f).coerceIn(1f, 5f)
//    }
//
//    private fun calculateVoiceClarityIndex(callQuality: CallQuality): Int {
//        // Simplified voice clarity calculation
//        val jitterImpact = 100 - (callQuality.jitterMillis.coerceIn(0, 100))
//        val packetLossImpact = 100 - callQuality.packetLossPercent.toInt().coerceIn(0, 100)
//        return ((jitterImpact + packetLossImpact) / 2)
//    }
//
//    private fun handleCallEnd() {
//        val callDuration = System.currentTimeMillis() - callStartTime
//        val wasDropped = callDuration < 30000 // Consider calls shorter than 30s as dropped
//
//        callHistory.add(CallMetrics(
//            duration = callDuration,
//            wasDropped = wasDropped,
//            setupTime = kpiData.callSetupTime
//        ))
//
//        updateCallStatistics()
//        callStartTime = 0
//    }
//
//    private fun updateCallStatistics() {
//        if (callHistory.isNotEmpty()) {
//            kpiData.apply {
//                dropCallRate = callHistory.count { it.wasDropped }.toFloat() / callHistory.size
//                callCompletionRate = 1 - dropCallRate
//                callSetupSuccessRate = callHistory.count { it.setupTime < 5000 }.toFloat() / callHistory.size
//                setupFailureRate = 1 - callSetupSuccessRate
//                callRetentionRate = callHistory.count { it.duration > 60000 }.toFloat() / callHistory.size
//            }
//        }
//    }
//
//    private fun startCallDurationTimer() {
//        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
//            override fun run() {
//                if (callStartTime > 0) {
//                    kpiData.callDuration = System.currentTimeMillis() - callStartTime
//                    updateKpis()
//                    Handler(Looper.getMainLooper()).postDelayed(this, 1000)
//                }
//            }
//        }, 1000)
//    }
//
//    private fun getNetworkTypeString(type: Int): String {
//        return when (type) {
//            TelephonyManager.NETWORK_TYPE_NR -> "5G"
//            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
//            TelephonyManager.NETWORK_TYPE_UMTS,
//            TelephonyManager.NETWORK_TYPE_HSDPA,
//            TelephonyManager.NETWORK_TYPE_HSUPA,
//            TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
//            TelephonyManager.NETWORK_TYPE_GPRS,
//            TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
//            else -> "Unknown"
//        }
//    }
//
//    private fun initializeBaseMetrics() {
//        kpiData.apply {
//            networkAvailability = telephonyManager.serviceState?.state == ServiceState.STATE_IN_SERVICE
//            voltePerformanceRating = if (telephonyManager.isVolteAvailable) 100 else 0
//            codecType = "AMR-WB" // Default VoLTE codec
//        }
//        updateKpis()
//    }
//
//    private fun updateKpis() {
//        callback?.onKpiUpdated(kpiData.copy())
//    }
//
//    private fun checkPermissions(): Boolean {
//        return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
//                android.content.pm.PackageManager.PERMISSION_GRANTED
//    }
//}