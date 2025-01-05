import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.*
import androidx.core.app.ActivityCompat
import java.util.concurrent.Executor

@SuppressLint("NewApi")
class CallMonitoringService(private val context: Context) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var callback: CallInfoCallback? = null

    // Interface to send call-related information to the UI
    interface CallInfoCallback {
        fun onCallInfoUpdated(info: CallInformation)
    }

    // Data class to hold all call-related information
    data class CallInformation(
        var callState: Int = CALL_STATE_IDLE,
        var signalStrength: Int = 0,
        var networkType: String = "",
        var serviceState: String = "",
        var simState: String = "",
        var networkSpeed: String = "",
        var isRoaming: Boolean = false,
        var disconnectCause: String = "",
        var callQuality: Int = 0,
        var callDirection: String = "",
        var dataActivity: String = "",
        var dataState: String = "",
        var carrierName: String = "",
        var cellularGeneration: String = "",
        var isEmergencyNumber: Boolean = false,
        var voiceMailNumber: String = "",
        var isConcurrentCallsAllowed: Boolean = false,
        var callDuration: Long = 0L
    )

    private val callInformation = CallInformation()


    private inner class CustomTelephonyCallback : TelephonyCallback(),
        TelephonyCallback.CallStateListener,
        TelephonyCallback.SignalStrengthsListener,
        TelephonyCallback.ServiceStateListener,
        TelephonyCallback.DisplayInfoListener,
        TelephonyCallback.DataActivityListener,
        TelephonyCallback.DataConnectionStateListener,
        TelephonyCallback.CarrierNetworkListener
        /*TelephonyCallback.CallQualityListener*/ {

        override fun onCallStateChanged(state: Int) {
            callInformation.callState = state
            CallLog.Calls.NUMBER
            when (state) {
                CALL_STATE_IDLE -> {
                    callInformation.callDirection = "Idle"
                    if (callInformation.callDuration > 0) {
                        // Call just ended
                        logDisconnectCause()
                    }
                }
                CALL_STATE_RINGING -> {
                    callInformation.callDirection = "Incoming"
                    callInformation.callDuration = 0
                }
                CALL_STATE_OFFHOOK -> {
                    if (callInformation.callDirection == "Idle") {
                        callInformation.callDirection = "Outgoing"
                    }
                    startCallDurationTimer()
                }
            }
            updateUI()
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            callInformation.signalStrength = signalStrength.level
            updateUI()
        }

        override fun onServiceStateChanged(serviceState: ServiceState) {
            callInformation.serviceState = when (serviceState.state) {
                ServiceState.STATE_IN_SERVICE -> "In Service"
                ServiceState.STATE_OUT_OF_SERVICE -> "Out of Service"
                ServiceState.STATE_EMERGENCY_ONLY -> "Emergency Only"
                ServiceState.STATE_POWER_OFF -> "Power Off"
                else -> "Unknown"
            }
            callInformation.isRoaming = serviceState.roaming
            updateUI()
        }

        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            callInformation.networkType = getNetworkTypeString(telephonyDisplayInfo.networkType)
            callInformation.cellularGeneration = getCellularGeneration(telephonyDisplayInfo.networkType)
            updateUI()
        }

        override fun onDataActivity(direction: Int) {
            callInformation.dataActivity = when (direction) {
                DATA_ACTIVITY_NONE -> "None"
                DATA_ACTIVITY_IN -> "Receiving"
                DATA_ACTIVITY_OUT -> "Sending"
                DATA_ACTIVITY_INOUT -> "Both"
                DATA_ACTIVITY_DORMANT -> "Dormant"
                else -> "Unknown"
            }
            updateUI()
        }

        override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
            callInformation.dataState = when (state) {
                DATA_CONNECTED -> "Connected"
                DATA_CONNECTING -> "Connecting"
                DATA_DISCONNECTED -> "Disconnected"
                DATA_SUSPENDED -> "Suspended"
                else -> "Unknown"
            }
            updateUI()
        }

        override fun onCarrierNetworkChange(active: Boolean) {
            callInformation.networkSpeed = if (active) "Carrier network changed" else "Normal"
            updateUI()
        }

//        override fun onCallQualityChanged(callQuality: android.telephony.CallQuality) {
//            callInformation.callQuality = callQuality.callQualityLevel
//            updateUI()
//        }
    }

    private val telephonyCallback = CustomTelephonyCallback()
    private val mainExecutor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        context.mainExecutor
    } else {
        Executor { runnable ->
            Handler(Looper.getMainLooper()).post(runnable)
        }
    }

    fun startMonitoring(callback: CallInfoCallback) {
        this.callback = callback
        if (checkPermissions()) {
            telephonyManager.registerTelephonyCallback(mainExecutor, telephonyCallback)
            initializeAdditionalInfo()
        }
    }

    fun stopMonitoring() {
        telephonyManager.unregisterTelephonyCallback(telephonyCallback)
        callback = null
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initializeAdditionalInfo() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED) {
            callInformation.apply {
                simState = getSimStateString(telephonyManager.simState)
                carrierName = telephonyManager.networkOperatorName ?: "Unknown"
                voiceMailNumber = telephonyManager?.voiceMailNumber ?: "Not available"
                isConcurrentCallsAllowed = telephonyManager.isConcurrentVoiceAndDataSupported
            }
            updateUI()
        }
    }

    private fun logDisconnectCause() {
        // Note: This is a simplified version. Actual disconnect cause might need to be obtained
        // through different means depending on Android version and device
        callInformation.disconnectCause = when {
            callInformation.signalStrength == 0 -> "Poor Signal"
            callInformation.serviceState == "Out of Service" -> "No Service"
            else -> "Normal"
        }
    }

    private var callDurationTimer: Handler? = null
    private fun startCallDurationTimer() {
        callDurationTimer = Handler(Looper.getMainLooper())
        callDurationTimer?.postDelayed(object : Runnable {
            override fun run() {
                callInformation.callDuration += 1
                updateUI()
                callDurationTimer?.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun updateUI() {
        callback?.onCallInfoUpdated(callInformation.copy())
    }

    private fun getNetworkTypeString(type: Int): String {
        return when (type) {
            NETWORK_TYPE_LTE -> "4G LTE"
            NETWORK_TYPE_NR -> "5G"
            NETWORK_TYPE_UMTS -> "3G"
            NETWORK_TYPE_EDGE -> "2G"
            else -> "Unknown"
        }
    }

    private fun getCellularGeneration(networkType: Int): String {
        return when (networkType) {
            NETWORK_TYPE_LTE -> "4G"
            NETWORK_TYPE_NR -> "5G"
            NETWORK_TYPE_UMTS, NETWORK_TYPE_HSDPA, NETWORK_TYPE_HSUPA, NETWORK_TYPE_HSPA -> "3G"
            NETWORK_TYPE_GPRS, NETWORK_TYPE_EDGE -> "2G"
            else -> "Unknown"
        }
    }

    private fun getSimStateString(state: Int): String {
        return when (state) {
            SIM_STATE_READY -> "Ready"
            SIM_STATE_ABSENT -> "Absent"
            SIM_STATE_PIN_REQUIRED -> "PIN Required"
            SIM_STATE_PUK_REQUIRED -> "PUK Required"
            SIM_STATE_NETWORK_LOCKED -> "Network Locked"
            else -> "Unknown"
        }
    }
}
