package com.accidentguard

import android.app.Application
import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.local.TokenManager

class AccidentGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val tokenManager = TokenManager(this)
        
        // Initialize Retrofit with the saved token provider and base URL
        RetrofitClient.init { tokenManager.getAuthToken() }
        RetrofitClient.updateBaseUrl(tokenManager.getServerUrl())
    }
}
