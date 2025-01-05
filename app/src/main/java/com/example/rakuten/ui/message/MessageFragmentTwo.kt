package com.example.rakuten.ui.message

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rakuten.databinding.FragmentMessageTwoBinding
import com.example.rakuten.ui.viewmodel.MessageViewModel
import com.example.rakuten.utils.showToast
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MessageFragmentTwo  : Fragment() {
    private lateinit var viewModel: MessageViewModel
    private lateinit var adapter: MessageAdapter

    private var _binding: FragmentMessageTwoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessageTwoBinding.inflate(inflater, container, false)
        checkRequestPermissions(requestPermissionsLauncher)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = MessageViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[MessageViewModel::class.java]
        setupRecyclerView()
        setupMessageInput()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter()
        binding.messagesList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@MessageFragmentTwo.adapter
        }
    }

    private fun setupMessageInput() {
        binding.sendButton.setOnClickListener {
            val phoneNumber = binding.phoneNumberInput.text.toString()
            val content = binding.messageInput.text.toString()

            if (phoneNumber.isBlank() || content.isBlank()) {
                showError("Please enter both phone number and message")
                return@setOnClickListener
            }

            viewModel.sendMessage(phoneNumber, content)
            clearInputs()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.updateMessages(messages)
                binding.emptyState.visibility =
                    if (messages.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.metrics.collect { metrics ->
                metrics?.let { updateMetricsDisplay(it) }
            }
        }
    }

    private fun updateMetricsDisplay(metrics: SmsManager.MessageMetrics) {
        binding.apply {
            totalMessagesText.text = "Total: ${metrics.totalMessagesSent}"
            successRateText.text = "Success: ${
                if (metrics.totalMessagesSent > 0)
                    "${(metrics.successfulDeliveries * 100 / metrics.totalMessagesSent)}%"
                else "0%"
            }"
            failureRateText.text = "Failed: ${metrics.failedDeliveries}"
            networkErrorsText.text = "Network Errors: ${metrics.networkErrors}"
            retryAttemptsText.text = "Retries: ${metrics.retryAttempts}"
        }
    }

    private fun clearInputs() {
        binding.apply {
            messageInput.text?.clear()
            phoneNumberInput.text?.clear()
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.SEND_SMS] == true &&
                    permissions[Manifest.permission.READ_PHONE_STATE] == true &&
                    permissions[Manifest.permission.RECEIVE_SMS] == true
            if (!granted) {
                showToast(requireContext(), "Permissions required for this feature.")
            }
        }

    fun checkRequestPermissions(requestPermissionsLauncher: ActivityResultLauncher<Array<String>>) {
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.RECEIVE_SMS
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.unresiterListner()
    }
}