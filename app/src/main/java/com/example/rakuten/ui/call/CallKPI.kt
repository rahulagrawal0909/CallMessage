package com.example.rakuten.ui.call

data class CallKPI(
    var callDuration: Long = 0,
    var callStatus: String = "Ideal",
    var disconnectedBy: String = "Disconnected: NA",
    var timeoutReason: String = "TimeOut: NA",
    var networkType: String = "NetworkType: NA",
    var strength: String = "Signal NA",
    var simCountry: String = "SimCountry NA"
)