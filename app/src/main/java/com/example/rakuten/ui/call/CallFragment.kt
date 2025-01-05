package com.example.rakuten.ui.call

import CallMonitoringService
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context.TELEPHONY_SERVICE
import android.content.Intent
import android.net.TrafficStats
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.rakuten.databinding.FragmentHomeBinding
import com.example.rakuten.utils.MyBottomSheetDialogFragment
import com.example.rakuten.utils.checkRequestPermissions
import com.example.rakuten.utils.getSignalStrengthDescription
import com.example.rakuten.utils.getSignalStrengthInfo
import com.example.rakuten.utils.getTelephonyManagerDetails
import com.example.rakuten.utils.hasPhoneStatePermission
import com.example.rakuten.utils.showToast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CallFragment : Fragment(), CallMonitoringService.CallInfoCallback {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding

    private var callEndReason: String = ""
    private var signaldetail: String =""
    private var callTimer: Handler? = null
    var isFirstTime = true

    private val initialTxBytes = TrafficStats.getMobileTxBytes()
    private val initialRxBytes = TrafficStats.getMobileRxBytes()
    private lateinit var callMonitoringService: CallMonitoringService

    private val telephonyManager: TelephonyManager by lazy {
        requireContext().getSystemService(TELEPHONY_SERVICE) as TelephonyManager
    }
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.READ_PHONE_STATE] == true &&
                    permissions[Manifest.permission.CALL_PHONE] == true &&
                    permissions[Manifest.permission.READ_CALL_LOG] == true
            if (!granted) {
                showToast(requireContext(), "Permissions required for this feature.")
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        initView()
        return binding?.root
    }

    private fun initView() {
        checkRequestPermissions(requestPermissionsLauncher)
        binding?.startCallButton?.setOnClickListener {
            val receiverNumber = binding?.receiverNumberInput?.text?.toString()
            val duration = binding?.callDurationInput?.text?.toString()?.toIntOrNull()
            if (receiverNumber?.isBlank() == true || duration == null || duration <= 0) {
                showToast(requireContext(), "Please enter valid inputs!")
            } else {
                receiverNumber?.let { it1 -> startVoiceCall(it1, duration) }
            }
        }

        binding?.btnSignalStrength?.setOnClickListener {
             MyBottomSheetDialogFragment.show(parentFragmentManager, signaldetail)
        }

        binding?.btnTelephoneDetail?.setOnClickListener {
            MyBottomSheetDialogFragment.show(parentFragmentManager, getTelephonyManagerDetails(telephonyManager))
        }
    }

    override fun onResume() {
        super.onResume()
        monitorCall(1)
    }

    private fun monitorCall(duration: Int) {
        monitorCallState(duration)
        fetchNetworkDetails()
        fetchSignalStrength()
    }

    private fun startVoiceCall(receiverNumber: String, duration: Int) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            showToast(requireContext(), "CALL_PHONE permission not granted!")
            return
        }
        startCallTimer(duration)
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$receiverNumber")
        }
        startActivity(callIntent)
    }

    private fun monitorCallState(duration: Int = 1) {
        if (!hasPhoneStatePermission(requireContext())) {
            showToast(requireContext(), "Permission not granted for phone state monitoring.")
            return
        }
        initializeCallMonitoring()
        telephonyManager.listen(object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (callTimer == null) {
                            if (isFirstTime) {
                                binding?.callStatusText?.text = "Call Status: Ideal"
                                isFirstTime = false
                            } else {
                                binding?.callStatusText?.text = "Call Status: Call Ended TimeOut"
                            }
                        } else {
                            if (isFirstTime) {
                                binding?.callStatusText?.text = "Call Status: Ideal"
                                isFirstTime = false
                            } else {
                                binding?.callStatusText?.text = "Call Status: Call Ended by User "
                            }
                        }
                    }

                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        binding?.callStatusText?.text = "Call Status: Call In Progress"
                    }

                    TelephonyManager.CALL_STATE_RINGING -> {
                        binding?.callStatusText?.text = "Call Status: Ringing , Incoming call"
                    }


                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE)
    }



    private fun fetchSignalStrength() {
        telephonyManager.listen(
            object : PhoneStateListener() {
                @RequiresApi(Build.VERSION_CODES.M)
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                    signalStrength?.let {
                        // Signal strength level (0-4)
                        signaldetail = getSignalStrengthInfo(signalStrength, true)
                        binding?.callStrengthText?.text = "Call Strength: \n" +
                                "Call Strength Level = ${getSignalStrengthDescription(it.level)} \n" +
                                "${getSignalStrengthInfo(signalStrength)}"

                    }
                }

                override fun onSignalStrengthChanged(asu: Int) {
                    super.onSignalStrengthChanged(asu)
                    if (asu < 5) {
                        // Assuming an ASU value less than 5 indicates poor signal
                        callEndReason = "call ended - NetworkWeekIssue"
                    }
                }

                override fun onDataConnectionStateChanged(networkType: Int) {
                    //super.onDataConnectionStateChanged(networkType)
                    val networkTypeName = when (networkType) {
                        TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3G)"
                        TelephonyManager.NETWORK_TYPE_EDGE -> "5G VoLTE Calls"
                        else -> "Unknown"
                    }
                    binding?.networkTypeText?.text = "NetworkInfo: \n" +
                            "Network type: $networkTypeName"
                }
            },
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
        )

    }

    private fun fetchNetworkDetails() {
        val telephonyManager =
            requireContext().getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val carrierName = telephonyManager.networkOperatorName
        val networkCountryIso = telephonyManager.networkCountryIso
        val simCountryIso = telephonyManager.simCountryIso
        val isRoaming = telephonyManager.isNetworkRoaming

        val currentTxBytes = TrafficStats.getMobileTxBytes()
        val currentRxBytes = TrafficStats.getMobileRxBytes()

        val transmitted = (currentTxBytes - initialTxBytes) / 1024 // KB
        val received = (currentRxBytes - initialRxBytes) / 1024 // KB

        binding?.otherDetailText?.text = "Network Detail: \n" +
                " CarrierName = $carrierName \n" +
                " Network Country Iso = $networkCountryIso \n" +
                " Sim Country Iso = $simCountryIso \n" +
                " IsRoaming = $isRoaming \n\n" +
                " DataUsage: \n" +
                " Transmitted = $transmitted KB \n" +
                " Received = $received KB"

    }

    private fun startCallTimer(duration: Int) {
//        val callDuration = duration // Duration in minutes
//        val intent = Intent(requireContext(), CallService::class.java).apply {
//            putExtra("DURATION", callDuration)
//        }
//        requireContext().startService(intent)

        callTimer = Handler(Looper.getMainLooper()).apply {
            postDelayed({
                // endCall()
                callEndReason = "Time's Up and Cut by User"
                binding?.callStatusText?.text = "Call Status: Ending Call - $callEndReason"
                // End Call (Optional, Requires Root or Accessibility Service)
                Toast.makeText(requireContext(), "Call Ended: $callEndReason", Toast.LENGTH_SHORT)
                    .show()
                fetchNetworkDetails()
                callTimer?.removeCallbacksAndMessages(null)
                callTimer = null
            }, duration * 60 * 1000L) // Convert minutes to milliseconds
        }

//        val callDuration = duration // Duration in minutes
//        val intent = Intent(requireContext(), CallService::class.java).apply {
//            putExtra("DURATION", callDuration)
//        }
//        CallService1.enqueueWork(requireContext(), intent)
//        telephonyManager

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initializeCallMonitoring() {
        callMonitoringService = CallMonitoringService(requireContext())
        callMonitoringService.startMonitoring(this)
    }

    override fun onCallInfoUpdated(info: CallMonitoringService.CallInformation) {
        var data: String = ""
        activity?.runOnUiThread {
            // Update all UI elements with the new information
            data = data + "Call State: ${getCallStateString(info.callState)}"
            data = data + "Signal Strength: ${info.signalStrength}/4"
            data = data + "Network Type: ${info.networkType}"
            data = data + "Service State: ${info.serviceState}"
            data = data + "SIM State: ${info.simState}"
            data = data + "Network Speed: ${info.networkSpeed}"
            data = data + "Call Direction: ${info.callDirection}"
            data = data + "Call Duration: ${formatCallDuration(info.callDuration)}"
            data = data + "Call Quality: ${getCallQualityString(info.callQuality)}"
            data = data + "Carrier: ${info.carrierName}"
            data = data + "Roaming: ${if (info.isRoaming) "Yes" else "No"}"
            data =
                data + "Disconnect Cause: ${info.disconnectCause.takeIf { it.isNotEmpty() } ?: "N/A"}"

            binding?.connectionText?.text = data
        }
    }

    private fun getCallStateString(state: Int): String {
        return when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "Idle"
            TelephonyManager.CALL_STATE_RINGING -> "Ringing"
            TelephonyManager.CALL_STATE_OFFHOOK -> "Off Hook"
            else -> "Unknown"
        }
    }

    private fun getCallQualityString(quality: Int): String {
        return when (quality) {
            0 -> "Poor"
            1 -> "Fair"
            2 -> "Good"
            3 -> "Excellent"
            else -> "Unknown"
        }
    }

    private fun formatCallDuration(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::callMonitoringService.isInitialized) {
            callMonitoringService.stopMonitoring()
        }
    }

}