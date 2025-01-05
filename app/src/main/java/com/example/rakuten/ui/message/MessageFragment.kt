package com.example.rakuten.ui.message

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.rakuten.databinding.FragmentMessageBinding
import com.example.rakuten.utils.MyBottomSheetDialogFragment
import com.example.rakuten.utils.appendKPI
import com.example.rakuten.utils.collectKPIs
import com.example.rakuten.utils.getTelephonyManagerDetails
import com.example.rakuten.utils.messageKpi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("UnspecifiedRegisterReceiverFlag")
class MessageFragment : Fragment() {

    private var _binding: FragmentMessageBinding? = null
    private val binding get() = _binding

    private val SMS_PERMISSION_CODE = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMessageBinding.inflate(inflater, container, false)

        binding?.btnSend?.setOnClickListener {
            if (checkSmsPermission()) {
                sendMessage()
            } else {
                requestSmsPermission()
            }
        }
        binding?.btnTelephoneDetail?.setOnClickListener {
            MyBottomSheetDialogFragment.show(parentFragmentManager,collectKPIs(requireContext(), "", "", true) )
        }
        return binding?.root
    }

    private fun sendMessage() {
        val receiverNumber = binding?.etReceiverNumber?.text.toString()
        val messageBody = binding?.etMessageBody?.text.toString()

        if (receiverNumber.isNotEmpty() && messageBody.isNotEmpty()) {
            try {
//                val smsManager: SmsManager = SmsManager.getDefault()
//                smsManager.sendTextMessage(receiverNumber, null, messageBody, null, null)
                sendSmsWithKPIs(receiverNumber, messageBody)
                //Toast.makeText(requireContext(), "Message Sent Successfully!", Toast.LENGTH_SHORT).show()

                // Clear the input fields
                binding?.etReceiverNumber?.text?.clear()
                binding?.etMessageBody?.text?.clear()

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Failed to send message: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(
                requireContext(),
                "Please enter both number and message",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @SuppressLint("UnsafeImplicitIntentLaunch")
    fun sendSmsWithKPIs(phoneNumber: String, message: String) {
        val smsManager = SmsManager.getDefault()
        val context = requireContext()

        val sentIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent("SMS_SENT"),
             PendingIntent.FLAG_IMMUTABLE
        )
        val deliveryIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent("SMS_DELIVERED"),
             PendingIntent.FLAG_IMMUTABLE
        )

        // Register Broadcast Receivers for Sent and Delivery Status
        requireContext().registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.e("Rahul", "SMS_SENT")
                val status = when (resultCode) {
                    Activity.RESULT_OK -> "Sent successfully"
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off"
                    else -> "Unknown"
                }
                appendKPI("Sent Status: $status")
                binding?.messageKpiText?.text = messageKpi
            }
        }, IntentFilter("SMS_SENT"), Context.RECEIVER_EXPORTED)

        requireContext().registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.e("Rahul", "SMS_DELIVERED")
                val status = when (resultCode) {
                    Activity.RESULT_OK -> "Delivered successfully"
                    Activity.RESULT_CANCELED -> "Delivery failed"
                    else -> "Pending"
                }
                appendKPI("Delivery Status: $status")
                appendKPI("Delivery Timestamp: ${System.currentTimeMillis()}")
                binding?.messageKpiText?.text = messageKpi
            }
        }, IntentFilter("SMS_DELIVERED"), Context.RECEIVER_EXPORTED)

        // Send SMS
        smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, deliveryIntent)

        // Collect KPIs
        val messageKpi = collectKPIs(requireContext(), phoneNumber, message)
        binding?.messageKpiText?.text = messageKpi
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            val intent = Intent("SMS_SENT")
            context.sendBroadcast(intent)
        }

    }


    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.SEND_SMS),
            SMS_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(requireContext(), "Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

}