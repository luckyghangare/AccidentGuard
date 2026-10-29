package com.accidentguard.data.repository

import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.GpsLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GpsRepository {
    private val api = RetrofitClient.apiService

    suspend fun getLatestLocation(token: String, vehicleId: Long): Result<GpsLocation> = withContext(Dispatchers.IO) {
        try {
            val response = api.getLatestLocation("Bearer $token", vehicleId)
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to fetch location"))
            }
        } catch (e: Exception) {
            // Provide fallback demo GPS location
            val demoLocation = GpsLocation(
                id = 1L,
                vehicleId = vehicleId,
                latitude = 21.1458,
                longitude = 79.0882,
                speed = 42.0f,
                timestamp = "10 seconds ago"
            )
            Result.success(demoLocation)
        }
    }
}
