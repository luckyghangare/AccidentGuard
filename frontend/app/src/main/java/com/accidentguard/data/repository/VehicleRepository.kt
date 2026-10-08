package com.accidentguard.data.repository

import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.Vehicle
import com.accidentguard.data.model.VehicleRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VehicleRepository {
    private val api = RetrofitClient.apiService

    suspend fun getVehicles(token: String): Result<List<Vehicle>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getVehicles("Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.data ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch vehicles"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun addVehicle(token: String, name: String, vehicleNumber: String, deviceId: String?): Result<Vehicle> = withContext(Dispatchers.IO) {
        try {
            val response = api.addVehicle("Bearer $token", VehicleRequest(name, vehicleNumber, deviceId))
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to add vehicle"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}
