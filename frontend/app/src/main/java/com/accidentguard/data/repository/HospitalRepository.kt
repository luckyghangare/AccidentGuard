package com.accidentguard.data.repository

import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.AmbulanceDispatchRequest
import com.accidentguard.data.model.EmergencyAlert
import com.accidentguard.data.model.Hospital
import com.accidentguard.data.model.HospitalStats
import com.accidentguard.data.model.VictimInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.accidentguard.data.local.TokenManager

class HospitalRepository {
    private val api = RetrofitClient.apiService

    suspend fun getAlerts(token: String): Result<List<EmergencyAlert>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getHospitalAlerts("Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.data ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.error?.message ?: "Failed to fetch alerts"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dispatchAmbulance(
        token: String,
        alertId: Int,
        ambulanceNumber: String = "AMB-01",
        driverName: String = "Emergency Unit",
        driverPhone: String = "108",
        etaMinutes: Int = 8,
        notes: String = "Direct ambulance dispatch from hospital dashboard"
    ): Result<EmergencyAlert> = withContext(Dispatchers.IO) {
        try {
            val request = AmbulanceDispatchRequest(
                ambulanceNumber = ambulanceNumber.ifBlank { "AMB-01" },
                driverName = driverName.ifBlank { "Emergency Unit" },
                driverPhone = driverPhone.ifBlank { "108" },
                etaMinutes = if (etaMinutes > 0) etaMinutes else 8,
                notes = notes.ifBlank { "Direct emergency dispatch" }
            )
            val response = api.dispatchAmbulance("Bearer $token", alertId, request)
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception(response.body()?.error?.message ?: "Dispatch failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFleet(token: String, totalAmbulances: Int?, availableAmbulances: Int?): Result<Hospital> = withContext(Dispatchers.IO) {
        try {
            val request = com.accidentguard.data.model.UpdateFleetRequest(
                totalAmbulances = totalAmbulances,
                availableAmbulances = availableAmbulances
            )
            val response = api.updateHospitalFleet("Bearer $token", request)
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception(response.body()?.error?.message ?: "Fleet update failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun admitPatient(token: String, alertId: Int, notes: String = "Patient admitted and stabilized"): Result<EmergencyAlert> = withContext(Dispatchers.IO) {
        try {
            val response = api.admitPatient("Bearer $token", alertId, mapOf("notes" to notes))
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception(response.body()?.error?.message ?: "Admission update failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStats(token: String): Result<HospitalStats> = withContext(Dispatchers.IO) {
        try {
            val response = api.getHospitalStats("Bearer $token")
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception(response.body()?.error?.message ?: "Failed to get stats"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
