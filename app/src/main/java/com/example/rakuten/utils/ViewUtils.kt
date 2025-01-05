package com.example.rakuten.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellSignalStrength
import android.telephony.SignalStrength
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat

fun hasPhoneStatePermission(context: Context): Boolean {
    return ActivityCompat.checkSelfPermission(
        context, Manifest.permission.READ_PHONE_STATE
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun checkRequestPermissions(requestPermissionsLauncher: ActivityResultLauncher<Array<String>>) {
    requestPermissionsLauncher.launch(
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
    )
}

fun showToast(context: Context, message: String) {
    Toast.makeText(
        context,
        message,
        Toast.LENGTH_SHORT
    ).show()
}

fun getSignalStrengthInfo(signalStrength: SignalStrength, isFullDetail: Boolean = false): String {
    val sb = StringBuilder()

    // Get cell signal strengths
    val cellSignalStrengths: List<CellSignalStrength> = signalStrength.getCellSignalStrengths()
    sb.append("Cell Signal Strengths:\n")
    for ((index, cellSignal) in cellSignalStrengths.withIndex()) {
        sb.append("Cell $index: ${cellSignal.toString()}\n")
    }

    // Add signal level
    sb.append("\nSignal Level: ${signalStrength.level}\n")

    // Add GSM specific values (deprecated methods)
    sb.append("\nGSM Signal Strength: ${signalStrength.gsmSignalStrength}\n")
    sb.append("GSM Bit Error Rate: ${signalStrength.gsmBitErrorRate}\n")

    // Add CDMA specific values (deprecated methods)
    sb.append("\nCDMA dBm: ${signalStrength.cdmaDbm}\n")
    sb.append("CDMA Ec/Io: ${signalStrength.cdmaEcio}\n")

    // Add EVDO specific values (deprecated methods)
    sb.append("\nEVDO dBm: ${signalStrength.evdoDbm}\n")
    sb.append("EVDO Ec/Io: ${signalStrength.evdoEcio}\n")
    sb.append("EVDO SNR: ${signalStrength.evdoSnr}\n")

    // Append the full string representation of SignalStrength
    if (isFullDetail)
        sb.append("\nFull Signal Strength Info: ${signalStrength.toString()}\n")

    return sb.toString()
}

@SuppressLint("MissingPermission")
fun getTelephonyManagerDetails(telephonyManager: TelephonyManager): String {
    val info = mutableListOf<String>()

    // Network Type
    val networkType = getNetworkType(telephonyManager.networkType)
    info.add("Network Type: $networkType")

    // Call State
    val callState = when (telephonyManager.callState) {
        TelephonyManager.CALL_STATE_IDLE -> "Idle"
        TelephonyManager.CALL_STATE_RINGING -> "Ringing"
        TelephonyManager.CALL_STATE_OFFHOOK -> "Off-Hook"
        else -> "Unknown"
    }
    info.add("Call State: $callState")

    // SIM State
    val simState = when (telephonyManager.simState) {
        TelephonyManager.SIM_STATE_READY -> "Ready"
        TelephonyManager.SIM_STATE_ABSENT -> "Absent"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Required"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Required"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
        TelephonyManager.SIM_STATE_UNKNOWN -> "Unknown"
        else -> "Other (${telephonyManager.simState})"
    }
    info.add("SIM State: $simState")

    // Voice Capabilities
    val isVoiceCapable = telephonyManager.isVoiceCapable
    info.add("Voice Capable: ${if (isVoiceCapable) "Yes" else "No"}")

    // Data State
    val dataState = when (telephonyManager.dataState) {
        TelephonyManager.DATA_CONNECTED -> "Connected"
        TelephonyManager.DATA_CONNECTING -> "Connecting"
        TelephonyManager.DATA_DISCONNECTED -> "Disconnected"
        TelephonyManager.DATA_SUSPENDED -> "Suspended"
        else -> "Unknown"
    }
    info.add("Data State: $dataState")

    // Carrier Info
    val carrierName = telephonyManager.networkOperatorName ?: "Unknown"
    info.add("Carrier Name: $carrierName")

    // Country ISO
    val countryIso = telephonyManager.networkCountryIso ?: "Unknown"
    info.add("Network Country ISO: $countryIso")

    // Mobile Network Code (MNC)
    val mnc = telephonyManager.networkOperator?.takeLast(2) ?: "Unknown"
    info.add("Mobile Network Code (MNC): $mnc")

    // Mobile Country Code (MCC)
    val mcc = telephonyManager.networkOperator?.take(3) ?: "Unknown"
    info.add("Mobile Country Code (MCC): $mcc")

    // Phone Type
    val phoneType = when (telephonyManager.phoneType) {
        TelephonyManager.PHONE_TYPE_GSM -> "GSM"
        TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
        TelephonyManager.PHONE_TYPE_SIP -> "SIP"
        TelephonyManager.PHONE_TYPE_NONE -> "None"
        else -> "Unknown"
    }
    info.add("Phone Type: $phoneType")

    // Is Roaming
    val isRoaming = telephonyManager.isNetworkRoaming
    info.add("Is Roaming: ${if (isRoaming) "Yes" else "No"}")

    var callDetail: String = ""
    info.forEach {
        callDetail = callDetail + "\n" + it
    }
    return callDetail
}

@SuppressLint("MissingPermission")
fun collectKPIs(context: Context, phoneNumber: String, message: String, isFullDetail: Boolean = false): String {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val kpiList = mutableListOf<String>()

    // Signal Strength (requires additional permissions)
    kpiList.add("Signal Strength: ${getSignalStrength(telephonyManager)}")

    // Carrier Info
    kpiList.add("Carrier Name: ${telephonyManager.networkOperatorName}")

    // Network Type
    val networkType = getNetworkType(telephonyManager.dataNetworkType)
    kpiList.add("Network Type: $networkType")

    val phoneType = when (telephonyManager.phoneType) {
        TelephonyManager.PHONE_TYPE_GSM -> "GSM"
        TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
        TelephonyManager.PHONE_TYPE_NONE -> "None"
        TelephonyManager.PHONE_TYPE_SIP -> "SIP"
        else -> "Unknown"
    }
    kpiList.add("Phone Type: $phoneType")

    // SIM State
    val simState = when (telephonyManager.simState) {
        TelephonyManager.SIM_STATE_READY -> "Ready"
        TelephonyManager.SIM_STATE_ABSENT -> "Absent"
        else -> "Other (${telephonyManager.simState})"
    }
    kpiList.add("SIM State: $simState")

    // Roaming Status
    kpiList.add("Roaming Status: ${if (telephonyManager.isNetworkRoaming) "Yes" else "No"}")

    // Message Details
    kpiList.add("Message Length: ${message.length}")
    kpiList.add("Message Parts: ${SmsManager.getDefault().divideMessage(message).size}")

    if(isFullDetail) {
        if(phoneNumber.isNullOrEmpty().not()) {
            val countryCode = phoneNumber.takeWhile { it.isDigit() }
            kpiList.add("Phone Number: ${phoneNumber}")
            kpiList.add("Country Code: $countryCode")
        }

        kpiList.add("SMS Encoding: GSM 7-bit")
        kpiList.add("Has ICC Card: ${telephonyManager.hasIccCard()}")
        kpiList.add("Is VoiceCapable: ${telephonyManager.isVoiceCapable}")
        //kpiList.add("ManualNetworkSelectionPlmn: ${telephonyManager.manualNetworkSelectionPlmn}")
        kpiList.add("IsConcurrentVoiceAndDataSupported: ${telephonyManager.isConcurrentVoiceAndDataSupported}")
        kpiList.add("isDataEnabled: ${telephonyManager.isDataEnabled}")

        kpiList.add("IsDataRoamingEnabled: ${telephonyManager.isDataRoamingEnabled}")
        kpiList.add("IsWorldPhone: ${telephonyManager.isWorldPhone}")
        kpiList.add("IsRttSupported: ${telephonyManager.isRttSupported}")
        if(phoneNumber.isNullOrEmpty().not()) {
            kpiList.add("IsEmergencyNumber: ${telephonyManager.isEmergencyNumber(phoneNumber)}")
        }
        kpiList.add("isDataEnabled: ${telephonyManager.isDataEnabled}")
    }


    // Append KPIs to TextView

    kpiList.forEach {
        appendKPI(it)
    }

    return messageKpi
}

var messageKpi: String = ""
fun appendKPI(kpi: String) {
    messageKpi = messageKpi + "\n" + kpi
}

fun getSignalStrength(telephonyManager: TelephonyManager): String {
    // You need to implement signal strength retrieval based on telephony listeners.
    return getSignalStrengthDescription(telephonyManager?.signalStrength?.level ?: 0)
}

fun getSignalStrengthDescription(level: Int): String {
    return when (level) {
        0 -> "No Signal Level 0"
        1 -> "Poor Signal Level 1"
        2 -> "Fair Signal Level 2"
        3 -> "Good Signal Level 3"
        4 -> "Excellent Signal Level 4"
        else -> "Unknown Signal Level"
    }
}
fun getNetworkType(networkType: Int): String {
   return when (networkType) {
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
       TelephonyManager.NETWORK_TYPE_GSM -> "GSM N/W"
        TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_UMTS -> "HSPA (3G)"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "VoLTE 2G"
        else -> "Cellular Unknown"
    }
}

