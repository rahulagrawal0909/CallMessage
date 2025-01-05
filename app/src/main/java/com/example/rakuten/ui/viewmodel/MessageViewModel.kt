package com.example.rakuten.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rakuten.ui.message.DeliveryStatus
import com.example.rakuten.ui.message.SmsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessageUiState(
    val id: String,
    val phoneNumber: String,
    val content: String,
    val status: DeliveryStatus,
    val timestamp: Long,
    val errorMessage: String? = null
)

@HiltViewModel
class MessageViewModel @Inject constructor(@ApplicationContext val context: Context) : ViewModel() {
    private val smsManager = SmsManager(context)

    private val _messages = MutableStateFlow<List<MessageUiState>>(emptyList())
    val messages: StateFlow<List<MessageUiState>> = _messages

    private val _metrics = MutableStateFlow<SmsManager.MessageMetrics?>(null)
    val metrics: StateFlow<SmsManager.MessageMetrics?> = _metrics

    init {
        observeDeliveryStatus()
        observeMetrics()
    }

    private fun observeDeliveryStatus() {
        smsManager.deliveryStatus.observeForever { status ->
            viewModelScope.launch {
                val currentMessages = _messages.value.toMutableList()
                val messageIndex = currentMessages.indexOfFirst { it.id == status.messageId }

                if (messageIndex != -1) {
                    currentMessages[messageIndex] = currentMessages[messageIndex].copy(
                        status = status.status,
                        errorMessage = status.errorMessage
                    )
                } else {
                    // Add new message to the list
                    currentMessages.add(0, MessageUiState(
                        id = status.messageId,
                        phoneNumber = status.phoneNumber,
                        content = "", // Will be updated when message is sent
                        status = status.status,
                        timestamp = status.timestamp,
                        errorMessage = status.errorMessage
                    ))
                }
                _messages.emit(currentMessages)
            }
        }
    }

    private fun observeMetrics() {
        smsManager.messageMetrics.observeForever { metrics ->
            viewModelScope.launch {
                _metrics.emit(metrics)
            }
        }
    }

    fun sendMessage(phoneNumber: String, content: String) {
        viewModelScope.launch {
            val messageId = smsManager.sendMessage(phoneNumber, content)
            // Update message content in the UI state
            val currentMessages = _messages.value.toMutableList()
            val messageIndex = currentMessages.indexOfFirst { it.id == messageId }
            if (messageIndex != -1) {
                currentMessages[messageIndex] = currentMessages[messageIndex].copy(
                    content = content
                )
                _messages.emit(currentMessages)
            }
        }
    }

    fun unresiterListner() {
        smsManager.cleanup()
    }
}
