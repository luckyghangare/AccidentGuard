package com.accidentguard.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.accidentguard.BuildConfig

object RetrofitClient {
    // Default fallback IP address or local dev URL. 
    // In production, this should be replaced by a stable URL via TokenManager.
    const val DEFAULT_BASE_URL = "https://api.ritiksawarkar.me/"
    
    var BASE_URL = DEFAULT_BASE_URL
        private set

    private var _apiService: ApiService? = null
    private var tokenProvider: (() -> String?)? = null

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        
        // Only add the Bearer token if an Authorization header isn't already present.
        if (original.header("Authorization") != null) {
            return@Interceptor chain.proceed(original)
        }

        val token = tokenProvider?.invoke()
        val request = if (!token.isNullOrEmpty()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Redact sensitive headers to prevent leakage in logs.
        redactHeader("Authorization")
        redactHeader("Cookie")
        
        // Set level based on build type: NONE for release, HEADERS for debug.
        // We avoid BODY logging to prevent accidental PII leakage in debug logs.
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun init(provider: () -> String?) {
        this.tokenProvider = provider
    }

    val apiService: ApiService
        get() {
            if (_apiService == null) {
                _apiService = buildApiService(BASE_URL)
            }
            return _apiService!!
        }

    /**
     * Updates the base URL dynamically (e.g., when the user changes server settings).
     * The input URL must be validated before calling this.
     */
    fun updateBaseUrl(newUrl: String) {
        val formatted = if (!newUrl.endsWith("/")) "$newUrl/" else newUrl
        if (formatted != BASE_URL) {
            BASE_URL = formatted
            _apiService = buildApiService(formatted)
        }
    }

    private fun buildApiService(url: String): ApiService {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
