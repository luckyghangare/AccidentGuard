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
            // Offline demo fallback alerts
            val demoAlerts = listOf(
                EmergencyAlert(
                    id = 1,
                    hospitalId = 1,
                    accidentId = 101,
                    status = "PENDING",
                    damageSeverity = "Critical / High Rollover Risk",
                    latitude = 21.1458,
                    longitude = 79.0882,
                    impactG = 8.5,
                    dateStr = "Today",
                    timeStr = "10:30 AM",
                    mapsUrl = "https://www.google.com/maps/search/?api=1&query=21.1458,79.0882",
                    victim = VictimInfo(
                        userName = "Lucky Ghangare",
                        userPhone = "+91 9876543210",
                        userEmail = "demo@accidentguard.com",
                        vehicleName = "Tata Nexon EV",
                        vehicleNumber = "MH-31-EQ-9922",
                        deviceId = "ESP32_AG_001"
                    ),
                    notes = "Rollover detected on National Highway 44. Immediate trauma response needed."
                ),
                EmergencyAlert(
                    id = 2,
                    hospitalId = 1,
                    accidentId = 102,
                    status = "PENDING",
                    damageSeverity = "High / Frontal Collision Impact",
                    latitude = 21.1530,
                    longitude = 79.0830,
                    impactG = 6.2,
                    dateStr = "Today",
                    timeStr = "11:15 AM",
                    mapsUrl = "https://www.google.com/maps/search/?api=1&query=21.1530,79.0830",
                    victim = VictimInfo(
                        userName = "Rajesh Sharma",
                        userPhone = "+91 9823011223",
                        userEmail = "rajesh.sharma@example.com",
                        vehicleName = "Mahindra XUV700",
                        vehicleNumber = "MH-31-FA-4411",
                        deviceId = "ESP32_AG_002"
                    ),
                    notes = "High-speed front collision near Sadar Square Flyover."
                ),
                EmergencyAlert(
                    id = 3,
                    hospitalId = 1,
                    accidentId = 103,
                    status = "DISPATCHED",
                    damageSeverity = "Moderate / Side-Impact Collision",
                    latitude = 21.1250,
                    longitude = 79.0520,
                    impactG = 4.8,
                    dateStr = "Today",
                    timeStr = "09:45 AM",
                    ambulanceNumber = "MH-31-AM-108",
                    driverName = "Ramesh Kumar",
                    driverPhone = "+91 9822001122",
                    etaMinutes = 8,
                    mapsUrl = "https://www.google.com/maps/search/?api=1&query=21.1250,79.0520",
                    victim = VictimInfo(
                        userName = "Priya Patel",
                        userPhone = "+91 9422155667",
                        userEmail = "priya.p@example.com",
                        vehicleName = "Hyundai Creta",
                        vehicleNumber = "MH-31-BZ-8820",
                        deviceId = "ESP32_AG_003"
                    ),
                    notes = "Life Line Ambulance unit #108 en route to Wardha Road."
                ),
                EmergencyAlert(
                    id = 4,
                    hospitalId = 1,
                    accidentId = 104,
                    status = "TREATED",
                    damageSeverity = "Moderate Impact / Stabilized",
                    latitude = 21.1400,
                    longitude = 79.0700,
                    impactG = 3.9,
                    dateStr = "Yesterday",
                    timeStr = "04:20 PM",
                    ambulanceNumber = "MH-31-AM-104",
                    driverName = "Suresh Patil",
                    driverPhone = "+91 9765432100",
                    etaMinutes = 0,
                    mapsUrl = "https://www.google.com/maps/search/?api=1&query=21.1400,79.0700",
                    victim = VictimInfo(
                        userName = "Amit Deshmukh",
                        userPhone = "+91 9765432109",
                        userEmail = "amit.d@example.com",
                        vehicleName = "Honda City",
                        vehicleNumber = "MH-31-CS-1234",
                        deviceId = "ESP32_AG_004"
                    ),
                    notes = "Victim admitted to Life Line Trauma ICU and condition is stable."
                )
            )
            Result.success(demoAlerts)
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

    suspend fun getStats(token: String, tokenManager: TokenManager? = null): Result<HospitalStats> = withContext(Dispatchers.IO) {
        try {
            val response = api.getHospitalStats("Bearer $token")
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception(response.body()?.error?.message ?: "Failed to get stats"))
            }
        } catch (e: Exception) {
            val hospName = tokenManager?.getUserName()?.ifBlank { "Life Line Emergency Hospital" } ?: "Life Line Emergency Hospital"
            val totalAmb = tokenManager?.getHospitalTotalAmbulances() ?: 8
            val availAmb = tokenManager?.getHospitalAvailableAmbulances() ?: 7
            val demoStats = HospitalStats(
                hospitalName = hospName,
                totalEmergencies = 4,
                pendingEmergencies = 2,
                activeDispatches = 1,
                admittedPatients = 1,
                totalAmbulances = totalAmb,
                availableAmbulances = availAmb
            )
            Result.success(demoStats)
        }
    }
}
