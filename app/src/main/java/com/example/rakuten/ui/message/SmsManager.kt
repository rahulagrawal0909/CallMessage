package com.example.rakuten.ui.message

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Telephony.Sms.Intents.SMS_DELIVER_ACTION
import android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.*

// Data class to represent SMS delivery status

data class SmsDeliveryStatus(
    val messageId: String,
    val phoneNumber: String,
    val status: DeliveryStatus,
    val timestamp: Long,
    val errorCode: Int? = null,
    val errorMessage: String? = null
)

enum class DeliveryStatus {
    SENDING,
    SENT,
    DELIVERED,
    FAILED,
    NETWORK_ERROR,
    INVALID_NUMBER,
    NO_SERVICE
}

class SmsManager(private val context: Context) {

    private val smsManager = SmsManager.getDefault()
    private val _deliveryStatus = MutableLiveData<SmsDeliveryStatus>()
    val deliveryStatus: LiveData<SmsDeliveryStatus> = _deliveryStatus

    // Track message delivery attempts and retries
    private val messageRetryCount = mutableMapOf<String, Int>()
    private val MAX_RETRY_ATTEMPTS = 3

    // KPIs tracking
    private val _messageMetrics = MutableLiveData<MessageMetrics>()
    val messageMetrics: LiveData<MessageMetrics> = _messageMetrics

    private var smsSentReceiver: BroadcastReceiver? = null
    private var smsDeliveredReceiver: BroadcastReceiver? = null

    /*private  val deliveryaction = "SMS_DELIVERED"
    private val sms_received= "SMS_SENT"
*/

    private val MESSAGE_SENT = "SMS_SENT"
    private val MESSAGE_DELIVERED = "SMS_DELIVERED"

    init {
        registerReceivers()
        updateInitialMetrics()
    }

    data class MessageMetrics(
        val totalMessagesSent: Int = 0,
        val successfulDeliveries: Int = 0,
        val failedDeliveries: Int = 0,
        val averageDeliveryTime: Long = 0,
        val networkErrors: Int = 0,
        val retryAttempts: Int = 0
    )

    fun sendMessage(phoneNumber: String, message: String): String {
        // Validate prerequisites
        val messageId = UUID.randomUUID().toString()
        if (!checkPrerequisites()) {
            updateDeliveryStatus(
                SmsDeliveryStatus(
                    messageId,
                    phoneNumber,
                    DeliveryStatus.NO_SERVICE,
                    System.currentTimeMillis()
                )
            )
            return "NO_SERVICE"
        }

        try {
            // Check for valid phone number
            if (!isValidPhoneNumber(phoneNumber)) {
                updateDeliveryStatus(
                    SmsDeliveryStatus(
                        messageId,
                        phoneNumber,
                        DeliveryStatus.INVALID_NUMBER,
                        System.currentTimeMillis()
                    )
                )
                return messageId
            }

            // Create pending intents for delivery tracking
            val sentIntent = createSentPendingIntent(messageId, phoneNumber)
            val deliveredIntent = createDeliveredPendingIntent(messageId, phoneNumber)

            // Handle long messages
            if (message.length > 160) {
                val messageParts = smsManager.divideMessage(message)
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()

                messageParts.forEach { _ ->
                    sentIntents.add(sentIntent)
                    deliveredIntents.add(deliveredIntent)
                }

                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    messageParts,
                    sentIntents,
                    deliveredIntents
                )
            } else {
                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    sentIntent,
                    deliveredIntent
                )
            }

            updateDeliveryStatus(
                SmsDeliveryStatus(
                    messageId,
                    phoneNumber,
                    DeliveryStatus.SENDING,
                    System.currentTimeMillis()
                )
            )

        } catch (e: Exception) {
            handleSendError(messageId, phoneNumber, e)
        }

        return messageId
    }

    private fun registerReceivers() {
        // SMS Sent receiver
        smsSentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedMessageId = intent?.getStringExtra("message_id")?:""
                val receivedPhoneNumber = intent?.getStringExtra("phone_number")?:""
               // val messageId = intent?.data?.lastPathSegment ?: return

                when (resultCode) {
                    Activity.RESULT_OK -> {
                        updateDeliveryStatus(
                            SmsDeliveryStatus(
                                receivedMessageId,
                                receivedPhoneNumber,
                                DeliveryStatus.SENT,
                                System.currentTimeMillis()
                            )
                        )
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> handleNoService(receivedMessageId)
                    SmsManager.RESULT_ERROR_RADIO_OFF -> handleNetworkError(receivedMessageId)
                    else -> handleSendFailure(receivedMessageId)
                }
            }
        }.also { receiver ->
            context.registerReceiver(
                receiver,
                IntentFilter(MESSAGE_SENT),
                Context.RECEIVER_EXPORTED
            )
        }

        // SMS Delivered receiver
        smsDeliveredReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedMessageId = intent?.getStringExtra("message_id")?:""
                val receivedPhoneNumber = intent?.getStringExtra("phone_number")?:""

                when (resultCode) {
                    Activity.RESULT_OK -> {
                        updateDeliveryStatus(
                            SmsDeliveryStatus(
                                receivedMessageId,
                                receivedPhoneNumber,
                                DeliveryStatus.DELIVERED,
                                System.currentTimeMillis()
                            )
                        )
                        updateMetrics(true)
                    }
                    Activity.RESULT_CANCELED -> {
                        handleDeliveryFailure(receivedMessageId)
                        updateMetrics(false)
                    }
                }
            }
        }.also { receiver ->
            context.registerReceiver(
                receiver,
                IntentFilter(MESSAGE_DELIVERED),Context.RECEIVER_EXPORTED
            )
        }
    }

    fun cleanup() {
        try {
            smsSentReceiver?.let {
                context.unregisterReceiver(it)
                smsSentReceiver = null
            }
        } catch (e: IllegalArgumentException) {
            // Receiver wasn't registered
        }

        try {
            smsDeliveredReceiver?.let {
                context.unregisterReceiver(it)
                smsDeliveredReceiver = null
            }
        } catch (e: IllegalArgumentException) {
            // Receiver wasn't registered
        }
    }

    private fun createSentPendingIntent(messageId: String, phoneNumber: String ): PendingIntent {
      var intent =   PendingIntent.getBroadcast(
            context,
            0,
            Intent(MESSAGE_SENT).apply {
                putExtra("message_id", messageId)
                putExtra("phone_number", phoneNumber)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return intent
    }

    private fun createDeliveredPendingIntent(messageId: String,phoneNumber: String ): PendingIntent {
        val deliveredIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(MESSAGE_DELIVERED).apply {
                putExtra("message_id", messageId)
                putExtra("phone_number", phoneNumber)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return deliveredIntent
    }

   /* private fun registerReceivers() {
        // Sent message receiver
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val messageId = intent?.action?.removePrefix("SMS_SENT_") ?: return

                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            updateDeliveryStatus(
                                SmsDeliveryStatus(
                                    messageId,
                                    intent.getStringExtra("phone_number") ?: "",
                                    DeliveryStatus.SENT,
                                    System.currentTimeMillis()
                                )
                            )
                        }
                        SmsManager.RESULT_ERROR_NO_SERVICE -> handleNoService(messageId)
                        SmsManager.RESULT_ERROR_RADIO_OFF -> handleNetworkError(messageId)
                        else -> handleSendFailure(messageId)
                    }
                }
            },
            IntentFilter("SMS_SENT").apply {
                // Add your app's package name
                addDataScheme(context.packageName)
            },
            Context.RECEIVER_NOT_EXPORTED // Specify receiver is not exported
        )

        // Delivery report receiver
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val messageId = intent?.action?.removePrefix("SMS_DELIVERED_") ?: return

                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            updateDeliveryStatus(
                                SmsDeliveryStatus(
                                    messageId,
                                    intent.getStringExtra("phone_number") ?: "",
                                    DeliveryStatus.DELIVERED,
                                    System.currentTimeMillis()
                                )
                            )
                            updateMetrics(true)
                        }
                        Activity.RESULT_CANCELED -> {
                            handleDeliveryFailure(messageId)
                            updateMetrics(false)
                        }
                    }
                }
            },
            IntentFilter("SMS_DELIVERED").apply {
                // Add your app's package name
                addDataScheme(context.packageName)
            },
            Context.RECEIVER_NOT_EXPORTED // Specify receiver is not exported
        )
    }*/

    // Clean up receivers when no longer needed
//    fun cleanup() {
//        try {
//            // Only unregister if the receivers were successfully registered
//            context.unregisterReceiver(/* your receiver reference */)
//        } catch (e: IllegalArgumentException) {
//            // Receiver wasn't registered
//        }
//    }

    /*private fun registerReceivers() {
        // Sent message receiver
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val messageId = intent?.action?.removePrefix("SMS_SENT_") ?: return

                when (resultCode) {
                    Activity.RESULT_OK -> {
                        updateDeliveryStatus(
                            SmsDeliveryStatus(
                                messageId,
                                intent.getStringExtra("phone_number") ?: "",
                                DeliveryStatus.SENT,
                                System.currentTimeMillis()
                            )
                        )
                    }

                    SmsManager.RESULT_ERROR_NO_SERVICE -> handleNoService(messageId)
                    SmsManager.RESULT_ERROR_RADIO_OFF -> handleNetworkError(messageId)
                    else -> handleSendFailure(messageId)
                }
            }
        }, IntentFilter("SMS_SENT"))

        // Delivery report receiver
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val messageId = intent?.action?.removePrefix("SMS_DELIVERED_") ?: return

                when (resultCode) {
                    Activity.RESULT_OK -> {
                        updateDeliveryStatus(
                            SmsDeliveryStatus(
                                messageId,
                                intent.getStringExtra("phone_number") ?: "",
                                DeliveryStatus.DELIVERED,
                                System.currentTimeMillis()
                            )
                        )
                        updateMetrics(true)
                    }

                    Activity.RESULT_CANCELED -> {
                        handleDeliveryFailure(messageId)
                        updateMetrics(false)
                    }
                }
            }
        }, IntentFilter("SMS_DELIVERED"))
    }*/

    private fun handleSendError(messageId: String, phoneNumber: String, error: Exception) {
        val retryCount = messageRetryCount.getOrDefault(messageId, 0)

        if (retryCount < MAX_RETRY_ATTEMPTS) {
            messageRetryCount[messageId] = retryCount + 1
            // Implement retry logic with exponential backoff
            // ...
        } else {
            updateDeliveryStatus(
                SmsDeliveryStatus(
                    messageId,
                    phoneNumber,
                    DeliveryStatus.FAILED,
                    System.currentTimeMillis(),
                    errorCode = -1,
                    errorMessage = error.message
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkPrerequisites(): Boolean {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        return when {
            telephonyManager.simState != TelephonyManager.SIM_STATE_READY -> false
            telephonyManager.networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN -> false
           // !context.hasPermission(android.Manifest.permission.SEND_SMS) -> false
            //!context.hasPermission(android.Manifest.permission.READ_PHONE_STATE) -> false
            else -> true
        }
    }

    /*private fun updateMetrics(successful: Boolean) {
        _messageMetrics.value = _messageMetrics.value?.let { metrics ->
            metrics.copy(
                totalMessagesSent = metrics.totalMessagesSent + 1,
                successfulDeliveries = metrics.successfulDeliveries + if (successful) 1 else 0,
                failedDeliveries = metrics.failedDeliveries + if (!successful) 1 else 0
            )
        } ?: MessageMetrics()
    }*/

    private fun updateInitialMetrics(){
        val currentMetrics = _messageMetrics.value ?: MessageMetrics()
        _messageMetrics.postValue(
            currentMetrics
        )
    }

    private fun updateMetrics(
        successful: Boolean = false,
        failed: Boolean = false,
        networkError: Boolean = false
    ) {
        val currentMetrics = _messageMetrics.value ?: MessageMetrics()

        _messageMetrics.postValue(
            currentMetrics.copy(
                totalMessagesSent = currentMetrics.totalMessagesSent + 1,
                successfulDeliveries = currentMetrics.successfulDeliveries + if (successful) 1 else 0,
                failedDeliveries = currentMetrics.failedDeliveries + if (failed) 1 else 0,
                networkErrors = currentMetrics.networkErrors + if (networkError) 1 else 0,
                retryAttempts = messageRetryCount.values.sum()
            )
        )
    }

    private fun updateDeliveryStatus(status: SmsDeliveryStatus) {
        _deliveryStatus.postValue(status)

        // Update metrics based on status
        when (status.status) {
            DeliveryStatus.NETWORK_ERROR -> updateMetrics(networkError = true)
            DeliveryStatus.FAILED -> updateMetrics(failed = true)
            DeliveryStatus.DELIVERED -> updateMetrics(successful = true)
            else -> {} // Other states don't affect metrics
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // Remove any whitespace and formatting
        val cleanNumber = phoneNumber.replace("\\s".toRegex(), "")

        return when {
            // Check basic length requirements (adjust for your country/use case)
            cleanNumber.length < 10 -> false
            cleanNumber.length > 15 -> false
            // Use Android's PhoneNumberUtils for basic validation
            !PhoneNumberUtils.isGlobalPhoneNumber(cleanNumber) -> false
            // Add country-specific validation if needed
            else -> true
        }
    }

    private fun handleNoService(messageId: String) {
        updateDeliveryStatus(
            SmsDeliveryStatus(
                messageId = messageId,
                phoneNumber = getPhoneNumberForMessage(messageId),
                status = DeliveryStatus.NO_SERVICE,
                timestamp = System.currentTimeMillis(),
                errorCode = SmsManager.RESULT_ERROR_NO_SERVICE,
                errorMessage = "No cellular service available"
            )
        )

        // Try to recover if possible
        if (shouldRetryForNoService(messageId)) {
            scheduleRetry(messageId)
        }
    }

    private fun handleNetworkError(messageId: String) {
        updateDeliveryStatus(
            SmsDeliveryStatus(
                messageId = messageId,
                phoneNumber = getPhoneNumberForMessage(messageId),
                status = DeliveryStatus.NETWORK_ERROR,
                timestamp = System.currentTimeMillis(),
                errorCode = SmsManager.RESULT_ERROR_RADIO_OFF,
                errorMessage = "Network error occurred"
            )
        )

        // Check if we should retry
        if (shouldRetryForNetworkError(messageId)) {
            scheduleRetry(messageId)
        }
    }

    private fun handleSendFailure(messageId: String) {
        val retryCount = messageRetryCount.getOrDefault(messageId, 0)

        if (retryCount < MAX_RETRY_ATTEMPTS) {
            messageRetryCount[messageId] = retryCount + 1
            scheduleRetry(messageId)
        } else {
            updateDeliveryStatus(
                SmsDeliveryStatus(
                    messageId = messageId,
                    phoneNumber = getPhoneNumberForMessage(messageId),
                    status = DeliveryStatus.FAILED,
                    timestamp = System.currentTimeMillis(),
                    errorCode = -1,
                    errorMessage = "Message failed after $MAX_RETRY_ATTEMPTS attempts"
                )
            )
        }
    }

    private fun handleDeliveryFailure(messageId: String) {
        updateDeliveryStatus(
            SmsDeliveryStatus(
                messageId = messageId,
                phoneNumber = getPhoneNumberForMessage(messageId),
                status = DeliveryStatus.FAILED,
                timestamp = System.currentTimeMillis(),
                errorCode = -1,
                errorMessage = "Delivery confirmation not received"
            )
        )
    }

    private fun shouldRetryForNoService(messageId: String): Boolean {
        return messageRetryCount.getOrDefault(messageId, 0) < MAX_RETRY_ATTEMPTS
    }

    private fun shouldRetryForNetworkError(messageId: String): Boolean {
        return messageRetryCount.getOrDefault(messageId, 0) < MAX_RETRY_ATTEMPTS
    }

    private fun scheduleRetry(messageId: String) {
        val retryCount = messageRetryCount.getOrDefault(messageId, 0)
        // Exponential backoff: 2s, 4s, 8s, etc.
        val delayMillis = (2000L * (1 shl retryCount))

        // Use Handler or WorkManager for production code
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Retry sending the message
            resendMessage(messageId)
        }, delayMillis)
    }

    private fun resendMessage(messageId: String) {
        // Implement message resending logic
        // You'll need to store the original message content and phone number
        // This is a simplified version
        val phoneNumber = getPhoneNumberForMessage(messageId)
        val content = getMessageContent(messageId)

        if (phoneNumber != null && content != null) {
            sendMessage(phoneNumber, content)
        } else {
            handleSendFailure(messageId)
        }
    }

    // Helper method to get phone number for a message ID
    private fun getPhoneNumberForMessage(messageId: String): String {
        // In a real implementation, you would store and retrieve this information
        // from a local database or cache
        return "" // Placeholder
    }

    // Helper method to get message content
    private fun getMessageContent(messageId: String): String? {
        // In a real implementation, you would store and retrieve this information
        // from a local database or cache
        return null // Placeholder
    }


        // Additional helper methods...
    }