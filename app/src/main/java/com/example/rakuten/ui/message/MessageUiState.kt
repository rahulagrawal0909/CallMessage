package com.example.rakuten.ui.message

data class MessageUiState(
    val id: String,
    val phoneNumber: String,
    val content: String,
    val status: DeliveryStatus,
    val timestamp: Long,
    val errorMessage: String? = null
)
