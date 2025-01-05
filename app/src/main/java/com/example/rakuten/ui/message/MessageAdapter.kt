package com.example.rakuten.ui.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.rakuten.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    private var messages: List<MessageUiState> = emptyList()

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Bind message data to views
        fun bind(message: MessageUiState) {
            itemView.apply {
                findViewById<TextView>(R.id.phoneNumberText).text = message.phoneNumber
                findViewById<TextView>(R.id.messageContentText).text = message.content
                findViewById<TextView>(R.id.statusText).apply {
                    text = message.status.name
                    setBackgroundColor(getStatusColor(message.status))
                }
                findViewById<TextView>(R.id.timestampText).text =
                    SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
                        .format(Date(message.timestamp))

                message.errorMessage?.let { error ->
                    findViewById<TextView>(R.id.errorText).apply {
                        visibility = View.VISIBLE
                        text = error
                    }
                } ?: run {
                    findViewById<TextView>(R.id.errorText).visibility = View.GONE
                }
            }
        }

        private fun getStatusColor(status: DeliveryStatus): Int {
            return when (status) {
                DeliveryStatus.DELIVERED -> R.color.success_green
                DeliveryStatus.FAILED -> R.color.error_red
                DeliveryStatus.SENDING -> R.color.pending_yellow
                else -> R.color.default_text
            }
        }
    }

    fun updateMessages(newMessages: List<MessageUiState>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size
}