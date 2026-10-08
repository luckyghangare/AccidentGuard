package com.accidentguard.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Default fallback IP address
    const val DEFAULT_BASE_URL = "http://192.168.1.35:8000/"
    var BASE_URL = DEFAULT_BASE_URL
        private set

    private var _apiService: ApiService? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService
        get() {
            if (_apiService == null) {
                _apiService = buildApiService(BASE_URL)
            }
            return _apiService!!
        }

    fun updateBaseUrl(newUrl: String) {
        var formatted = newUrl.trim()
        if (formatted.isNotEmpty()) {
            if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
                formatted = "http://$formatted"
            }
            if (!formatted.endsWith("/")) {
                formatted = "$formatted/"
            }
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

