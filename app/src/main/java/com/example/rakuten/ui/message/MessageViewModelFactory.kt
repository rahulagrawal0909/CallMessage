package com.example.rakuten.ui.message

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.rakuten.ui.viewmodel.MessageViewModel

class MessageViewModelFactory(private val context: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MessageViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}