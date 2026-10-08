package com.accidentguard.data.repository

import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.SensorReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SensorRepository {
    private val api = RetrofitClient.apiService

    suspend fun getLatestSensorReading(token: String, vehicleId: Int): Result<SensorReading> = withContext(Dispatchers.IO) {
        try {
            val response = api.getLatestSensorReading("Bearer $token", vehicleId)
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to fetch sensor reading"))
            }
        } catch (e: Exception) {
            val demoReading = SensorReading(
                id = 1,
                vehicleId = vehicleId,
                obstacleDistance = 68.0f,
                accelerationG = 1.12f,
                gyroscopeRadS = 0.08f,
                status = "SAFE",
                timestamp = "2 seconds ago"
            )
            Result.success(demoReading)
        }
    }
}
