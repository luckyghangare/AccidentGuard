package com.accidentguard.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.manager.LiveKitManager
import com.accidentguard.data.repository.CallRepository
import com.accidentguard.ui.call.CallViewModel

class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    
    private val tokenManager = TokenManager(context)
    private val apiService = RetrofitClient.apiService
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(CallViewModel::class.java) -> {
                CallViewModel(
                    repository = CallRepository(apiService),
                    tokenManager = tokenManager
                ) as T
            }
            // Add other ViewModels here if they need custom constructor injection
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
