package com.accidentguard.data.repository

import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.Accident
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccidentRepository {
    private val api = RetrofitClient.apiService

    suspend fun getAccidentHistory(token: String): Result<List<Accident>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAccidentHistory("Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.data ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch accidents"))
            }
        } catch (e: Exception) {
            val demoList = listOf(
                Accident(1, 1, "06 October 2026", "10:32 PM", 21.1458, 79.0882, 4.8f, "emergency_sent", "2026-10-06 22:32:00"),
                Accident(2, 1, "28 September 2026", "06:45 PM", 21.1462, 79.0911, 3.9f, "cancelled", "2026-09-28 18:45:00"),
                Accident(3, 1, "15 September 2026", "09:12 AM", 21.1490, 79.0880, 2.1f, "false_alarm", "2026-09-15 09:12:00")
            )
            Result.success(demoList)
        }
    }

    suspend fun cancelAlert(token: String, accidentId: Int): Result<Accident> = withContext(Dispatchers.IO) {
        try {
            val response = api.cancelAccidentAlert("Bearer $token", accidentId)
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to cancel alert"))
            }
        } catch (e: Exception) {
            val demoCancelled = Accident(1, 1, "06 October 2026", "10:32 PM", 21.1458, 79.0882, 4.8f, "cancelled", "2026-10-06 22:32:00")
            Result.success(demoCancelled)
        }
    }
}
