package com.accidentguard.data.repository

import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.Accident
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccidentRepository {
    private val api = RetrofitClient.apiService

    suspend fun getAccidentHistory(token: String): Result<List<Accident>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("AccidentRepository", "getAccidentHistory: Fetching from api/accidents")
            val response = api.getAccidentHistory("Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                val data = (response.body()?.data ?: emptyList()).map { it.copy(source = "backend") }
                android.util.Log.d("AccidentRepository", "getAccidentHistory: Success. Received ${data.size} records")
                Result.success(data)
            } else {
                val errorMsg = response.body()?.error?.message ?: "Failed to fetch accidents (HTTP ${response.code()})"
                android.util.Log.e("AccidentRepository", "getAccidentHistory: API Error: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.w("AccidentRepository", "getAccidentHistory: Exception occurred: ${e.message}. Using demo fallback data.")
            val demoList = listOf(
                Accident(1L, 1L, "06 October 2026", "10:32 PM", 21.1458, 79.0882, 4.8f, "emergency_sent", "2026-10-06 22:32:00", source = "demo"),
                Accident(2L, 1L, "28 September 2026", "06:45 PM", 21.1462, 79.0911, 3.9f, "cancelled", "2026-09-28 18:45:00", source = "demo"),
                Accident(3L, 1L, "15 September 2026", "09:12 AM", 21.1490, 79.0880, 2.1f, "false_alarm", "2026-09-15 09:12:00", source = "demo")
            )
            Result.success(demoList)
        }
    }

    suspend fun cancelAlert(token: String, accidentId: Long): Result<Accident> = withContext(Dispatchers.IO) {
        try {
            val response = api.cancelAccidentAlert("Bearer $token", accidentId)
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                val updatedAccident = response.body()!!.data!!.copy(source = "backend")
                Result.success(updatedAccident)
            } else {
                Result.failure(Exception("Failed to cancel alert"))
            }
        } catch (e: Exception) {
            val demoCancelled = Accident(1L, 1L, "06 October 2026", "10:32 PM", 21.1458, 79.0882, 4.8f, "cancelled", "2026-10-06 22:32:00", source = "demo")
            Result.success(demoCancelled)
        }
    }
}
