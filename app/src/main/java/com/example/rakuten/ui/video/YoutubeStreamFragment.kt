package com.example.rakuten.ui.video

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.rakuten.databinding.FragmentPlayerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class YoutubeStreamFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    var isWifiConnected = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        binding?.playButton?.setOnClickListener {
            if (isWifiConnected) {
                Toast.makeText(
                    requireContext(),
                    "You are connected with wifi please connect with 4G or 5G network",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                requireContext().startActivity(Intent(requireContext(), VideoActivity::class.java))
            }
        }
        takeDecisionBasedOnNetwork(requireContext())
        return binding.root
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun getNetworkType(context: Context): String {
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val network = connectivityManager.activeNetwork ?: return "No Connection"
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return "No Connection"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                "Wi-Fi"
            }

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.READ_PHONE_STATE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                    return "Permission not granted"
                }
                when (telephonyManager.networkType) {
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                    else -> "Cellular Unknown"
                }
            }

            else -> "No Connection"
        }
    }

    private fun takeDecisionBasedOnNetwork(context: Context) {
        var text: String ?= null
        when (val networkType = getNetworkType(context)) {
            "Wi-Fi" -> {
                // Handle Wi-Fi logic
                isWifiConnected = true
                text = "Connected to Wi-Fi. Perform high-bandwidth operations."
            }

            "4G" -> {
                isWifiConnected = false
                // Handle 4G logic
                text = "Connected to 4G. Perform medium-bandwidth operations."
            }

            "5G" -> {
                // Handle 5G logic
                isWifiConnected = false
                text = "Connected to 5G. Perform high-bandwidth operations."
            }

            "No Connection" -> {
                // Handle no connection logic
                text = "No network connection. Alert the user or retry."
            }

            else -> {
                // Handle other cases
                text = "Connected to: $networkType. Perform generic operations."
            }
        }
        binding.wifiDetail.text = text
    }
}