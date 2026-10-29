package com.accidentguard.data.repository

import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.AmbulanceDispatchRequest
import com.accidentguard.data.model.EmergencyAlert
import com.accidentguard.data.model.Hospital
import com.accidentguard.data.model.HospitalStats
import com.accidentguard.data.model.VictimInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

import com.accidentguard.data.local.TokenManager

class HospitalRepository {
    private val api = RetrofitClient.apiService
    private val okHttpClient = OkHttpClient()

    suspend fun getAlerts(token: String): Result<List<EmergencyAlert>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("HospitalRepository", "getAlerts: Fetching emergency alerts")
            val response = api.getHospitalAlerts("Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data ?: emptyList()
                android.util.Log.d("HospitalRepository", "getAlerts: Success. Received ${data.size} alerts")
                Result.success(data)
            } else {
                android.util.Log.e("HospitalRepository", "getAlerts: API Error: HTTP ${response.code()}")
                Result.failure(Exception(response.body()?.error?.message ?: "Failed to fetch alerts"))
            }
        } catch (e: Exception) {
            android.util.Log.e("HospitalRepository", "getAlerts: Exception: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun dispatchAmbulance(
        token: String,
        alertId: Long,
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

    suspend fun admitPatient(token: String, alertId: Long, notes: String = "Patient admitted and stabilized"): Result<EmergencyAlert> = withContext(Dispatchers.IO) {
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

    suspend fun getNearbyHospitals(token: String, lat: Double, lon: Double): Result<List<Hospital>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("HospitalRepository", "getNearbyHospitals: Fetching for Lat: $lat, Lon: $lon")
            val response = api.getNearbyHospitals("Bearer $token", lat, lon)
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data ?: emptyList()
                android.util.Log.d("HospitalRepository", "getNearbyHospitals: Success. Received ${data.size} hospitals")
                
                // If backend returns empty list, trigger OSM fallback anyway
                if (data.isEmpty()) {
                    android.util.Log.d("HospitalRepository", "getNearbyHospitals: Backend empty. Falling back to OSM.")
                    getHospitalsFromOSM(lat, lon)
                } else {
                    Result.success(data.map { it.copy(source = "backend") })
                }
            } else {
                android.util.Log.e("HospitalRepository", "getNearbyHospitals: API Error: HTTP ${response.code()}")
                getHospitalsFromOSM(lat, lon)
            }
        } catch (e: Exception) {
            android.util.Log.w("HospitalRepository", "getNearbyHospitals: Exception: ${e.message}. Trying OSM.")
            getHospitalsFromOSM(lat, lon)
        }
    }

    suspend fun getHospitalsFromOSM(lat: Double, lon: Double): Result<List<Hospital>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("HospitalRepository", "getHospitalsFromOSM: Querying Overpass API for Lat: $lat, Lon: $lon")
            
            // Overpass QL query: find nodes tagged as amenity=hospital within 5000m of coordinates
            val query = """
                [out:json];
                node["amenity"="hospital"](around:5000,$lat,$lon);
                out body;
            """.trimIndent()
            
            val url = "https://overpass-api.de/api/interpreter?data=${java.net.URLEncoder.encode(query, "UTF-8")}"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AccidentGuard-Android-App/1.0.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Overpass API error: ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response from OSM")
            val json = JSONObject(responseBody)
            val elements = json.getJSONArray("elements")
            
            val osmHospitals = mutableListOf<Hospital>()
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                val tags = element.optJSONObject("tags")
                val name = tags?.optString("name") ?: "OSM Hospital"
                val hospital = Hospital(
                    id = element.getLong("id"),
                    name = name,
                    latitude = element.getDouble("lat"),
                    longitude = element.getDouble("lon"),
                    address = tags?.optString("addr:full") ?: tags?.optString("addr:street") ?: "OSM Data",
                    source = "osm"
                )
                osmHospitals.add(hospital)
            }
            
            android.util.Log.d("HospitalRepository", "getHospitalsFromOSM: Found ${osmHospitals.size} hospitals")
            
            if (osmHospitals.isEmpty()) {
                Result.success(getNagpurDemoHospitals())
            } else {
                Result.success(osmHospitals)
            }
        } catch (e: Exception) {
            android.util.Log.e("HospitalRepository", "getHospitalsFromOSM: Failed: ${e.message}")
            Result.success(getNagpurDemoHospitals())
        }
    }

    private fun getNagpurDemoHospitals(): List<Hospital> {
        return listOf(
            Hospital(101L, "Nagpur General Hospital (Demo)", "contact@ngh.org", "0712-2222222", "Kingsway, Nagpur", "Nagpur", 21.1500, 79.0800, source = "demo"),
            Hospital(102L, "LifeLine Trauma Center (Demo)", "info@lifeline.in", "0712-3333333", "Ramdaspeth, Nagpur", "Nagpur", 21.1400, 79.0700, source = "demo"),
            Hospital(103L, "Orange City Medical (Demo)", "admin@ocmc.com", "0712-4444444", "Dhantoli, Nagpur", "Nagpur", 21.1350, 79.0900, source = "demo")
        )
    }
}
