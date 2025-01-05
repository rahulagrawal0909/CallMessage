package com.example.rakuten.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
) : ViewModel() {

//    private val _callState = MutableLiveData<CallEntity>()
//    val callState: LiveData<CallEntity> get() = _callState

    private val _kpiState = MutableLiveData<List<Pair<String, String>>>()
    val kpiState: LiveData<List<Pair<String, String>>> get() = _kpiState

    fun initiateCall(receiverNumber: String, duration: Int) {
        viewModelScope.launch {
//            val call = triggerCallUseCase(receiverNumber, duration)
//            _callState.postValue(call)
        }
    }

    fun fetchKPIs() {
        viewModelScope.launch {
//            val kpis = getKPIsUseCase()
//            _kpiState.postValue(kpis)
        }
    }
}
