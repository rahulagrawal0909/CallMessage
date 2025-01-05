package com.example.rakuten.ui.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.rakuten.databinding.FragmentHomeBinding
import com.example.rakuten.utils.MyBottomSheetDialogFragment
import com.example.rakuten.utils.checkRequestPermissions
import com.example.rakuten.utils.getNetworkType
import com.example.rakuten.utils.getSignalStrengthDescription
import com.example.rakuten.utils.getSignalStrengthInfo
import com.example.rakuten.utils.getTelephonyManagerDetails
import com.example.rakuten.utils.hasPhoneStatePermission
import com.example.rakuten.utils.showToast
import dagger.hilt.android.AndroidEntryPoint
import java.util.Timer
import java.util.TimerTask

@Suppress("DEPRECATION")


@AndroidEntryPoint
class CallFragment : Fragment() {

    private var phoneStateListener: PhoneStateListener? = null
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding

    private var callEndReason: String = ""
    private var signaldetail: String = ""
    private var callStartTime: Long = 0
    private var callEndTime: Long = 0
    private var isCallActive = false
    private val callKPI = CallKPI()

    private val handler = Handler(Looper.getMainLooper())
    private var callTimer: Timer? = null

    private val initialTxBytes = TrafficStats.getMobileTxBytes()
    private val initialRxBytes = TrafficStats.getMobileRxBytes()

    private val telephonyManager: TelephonyManager by lazy {
        requireContext().getSystemService(TELEPHONY_SERVICE) as TelephonyManager
    }
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.READ_PHONE_STATE] == true &&
                    permissions[Manifest.permission.CALL_PHONE] == true &&
                    permissions[Manifest.permission.ANSWER_PHONE_CALLS] == true
            if (!granted) {
                //showToast(requireContext(), "Permissions required for this feature.")
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
            MyBottomSheetDialogFragment.show(
                parentFragmentManager,
                getTelephonyManagerDetails(telephonyManager)
            )
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
            PackageManager.PERMISSION_GRANTED
        ) {
            showToast(requireContext(), "CALL_PHONE permission not granted!")
            return
        }
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

        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_IDLE -> {
                        callKPI.callStatus = "Call Status: IDLE"
                        if (isCallActive) {
                            callEndTime = System.currentTimeMillis()
                            callKPI.callDuration = callEndTime - callStartTime

                            // Determine who disconnected the call
                            if (callKPI.timeoutReason.isNotEmpty()) {
                                Log.d("CallMonitoring", "Call ended due to timeout")
                            } else {
                                // This is a simplified logic. In real implementation,
                                // you might need to use additional APIs or signals
                                // to determine who exactly ended the call
                                if (callKPI.callDuration < (duration * 60000)) {
                                    callKPI.disconnectedBy = "Manual disconnection"
                                }
                            }

                            //printCallSummary()
                            stopCallTimer()
                        }
                        printCallSummary()
                        isCallActive = false
                    }

                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        // binding?.callStatusText?.text = "Call Status: Call In Progress"
                        callKPI.callStatus = "ACTIVE"
                        if (!isCallActive) {
                            callStartTime = System.currentTimeMillis()
                            isCallActive = true
                            startCallTimer(duration * 60000L)
                        }
                        printCallSummary()
                    }

                    TelephonyManager.CALL_STATE_RINGING -> {
                        callKPI.callStatus = "Call Status: RINGING"
                        printCallSummary()
                        //binding?.callStatusText?.text = "Call Status: Ringing , Incoming call"
                    }
                }
            }
        }
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun startCallTimer(duration: Long) {
        callTimer = Timer()
        callTimer?.schedule(object : TimerTask() {
            override fun run() {
                handler.post {
                    if (isCallActive) {
                        callKPI.timeoutReason = "Call EndTime Exceeded By Given Duration "
                        endCall()
                    }
                }
            }
        }, duration)
    }

    private fun stopCallTimer() {
        callTimer?.cancel()
        callTimer = null
    }

    private fun endCall() {
        try {
            // Get the TelecomManager instance
            val telecomManager =
                requireContext().getSystemService(Context.TELECOM_SERVICE) as TelecomManager

            // Check for permission
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ANSWER_PHONE_CALLS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("CallMonitoring", "Missing ANSWER_PHONE_CALLS permission")
                return
            }
            // End the call using TelecomManager
            if (telecomManager.isInCall) {
                telecomManager.endCall()
                Log.d("CallMonitoring", "Call ended successfully")
            }
        } catch (e: Exception) {
            Log.e("CallMonitoring", "Error ending call", e)
        }
    }

    private fun printCallSummary() {
        val summary ="Call Summary:\n" +
                "Status: ${callKPI.callStatus}\n" +
                "Duration: ${callKPI.callDuration / 1000} seconds\n" +
                "Disconnected by: ${callKPI.disconnectedBy}\n" +
                "${if (callKPI.timeoutReason.isNotEmpty()) callKPI.timeoutReason else ""}"

        binding?.strength?.text = callKPI.strength
        binding?.simCountry?.text = callKPI.simCountry
        binding?.simCountry?.text = callKPI.simCountry
        binding?.networkType?.text = callKPI.networkType
        binding?.disconnectReason?.text = callKPI.disconnectedBy +"${if (callKPI.timeoutReason.isNotEmpty()) callKPI.timeoutReason else ""}"
        binding?.duration?.text = "${callKPI.callDuration / 1000} seconds"
        binding?.status?.text = callKPI.callStatus

        binding?.callStatusText?.text = summary
    }


    private fun fetchSignalStrength() {
        telephonyManager.listen(
            object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                    signalStrength?.let {
                        // Signal strength level (0-4)
                        signaldetail = getSignalStrengthInfo(signalStrength, true)
                        val signal = getSignalStrengthDescription(it.level)
                        binding?.callStrengthText?.text = "Call Strength: \n" +
                                "Call Strength Level = ${signal} \n" +
                                "${getSignalStrengthInfo(signalStrength)}"
                        callKPI.strength = signal
                        printCallSummary()
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
                    val networkTypeName = getNetworkType(networkType)
                    callKPI.networkType = networkTypeName
                    binding?.networkTypeText?.text = "NetworkInfo: \n" +
                            "Network type: $networkTypeName"
                }
            },
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
        )

    }

    @SuppressLint("SetTextI18n")
    private fun fetchNetworkDetails() {
        val carrierName = telephonyManager.networkOperatorName
        val networkCountryIso = telephonyManager.networkCountryIso
        val simCountryIso = telephonyManager.simCountryIso
        val isRoaming = telephonyManager.isNetworkRoaming

        val currentTxBytes = TrafficStats.getMobileTxBytes()
        val currentRxBytes = TrafficStats.getMobileRxBytes()

        val transmitted = (currentTxBytes - initialTxBytes) / 1024 // KB
        val received = (currentRxBytes - initialRxBytes) / 1024 // KB
        callKPI.simCountry = "Sim Country Iso "+simCountryIso
        binding?.otherDetailText?.text = "Network Detail: \n" +
                " CarrierName = $carrierName \n" +
                " Network Country Iso = $networkCountryIso \n" +
                " Sim Country Iso = $simCountryIso \n" +
                " IsRoaming = $isRoaming \n\n" +
                " DataUsage: \n" +
                " Transmitted = $transmitted KB \n" +
                " Received = $received KB"

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        stopCallTimer()
    }

}