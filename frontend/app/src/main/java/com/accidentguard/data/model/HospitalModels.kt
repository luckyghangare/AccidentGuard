package com.accidentguard.data.model

import com.google.gson.annotations.SerializedName

data class Hospital(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("email") val email: String = "",
    @SerializedName("phone_number") val phoneNumber: String = "",
    @SerializedName("address") val address: String = "",
    @SerializedName("city") val city: String = "Nagpur",
    @SerializedName("latitude") val latitude: Double = 21.1458,
    @SerializedName("longitude") val longitude: Double = 79.0882,
    @SerializedName("total_ambulances") val totalAmbulances: Int = 5,
    @SerializedName("available_ambulances") val availableAmbulances: Int = 5,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class HospitalLoginData(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("hospital") val hospital: Hospital
)

data class HospitalLoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class HospitalRegisterRequest(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("password") val password: String,
    @SerializedName("address") val address: String,
    @SerializedName("city") val city: String = "Nagpur",
    @SerializedName("latitude") val latitude: Double = 21.1458,
    @SerializedName("longitude") val longitude: Double = 79.0882,
    @SerializedName("total_ambulances") val totalAmbulances: Int = 5
)

data class VictimInfo(
    @SerializedName("user_name") val userName: String = "Unknown Driver",
    @SerializedName("user_phone") val userPhone: String = "N/A",
    @SerializedName("user_email") val userEmail: String = "N/A",
    @SerializedName("vehicle_name") val vehicleName: String = "Unknown Vehicle",
    @SerializedName("vehicle_number") val vehicleNumber: String = "N/A",
    @SerializedName("device_id") val deviceId: String? = null
)

data class EmergencyAlert(
    @SerializedName("id") val id: Int,
    @SerializedName("hospital_id") val hospitalId: Int,
    @SerializedName("accident_id") val accidentId: Int,
    @SerializedName("status") val status: String,
    @SerializedName("damage_severity") val damageSeverity: String = "Critical",
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("impact_g") val impactG: Double,
    @SerializedName("date_str") val dateStr: String,
    @SerializedName("time_str") val timeStr: String,
    @SerializedName("maps_url") val mapsUrl: String,
    @SerializedName("victim") val victim: VictimInfo,
    @SerializedName("ambulance_number") val ambulanceNumber: String? = null,
    @SerializedName("driver_name") val driverName: String? = null,
    @SerializedName("driver_phone") val driverPhone: String? = null,
    @SerializedName("eta_minutes") val etaMinutes: Int? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("dispatched_at") val dispatchedAt: String? = null,
    @SerializedName("admitted_at") val admittedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class AmbulanceDispatchRequest(
    @SerializedName("ambulance_number") val ambulanceNumber: String = "AMB-01",
    @SerializedName("driver_name") val driverName: String = "Emergency Unit",
    @SerializedName("driver_phone") val driverPhone: String = "108",
    @SerializedName("eta_minutes") val etaMinutes: Int = 8,
    @SerializedName("notes") val notes: String? = "Direct ambulance dispatch from hospital dashboard"
)

data class UpdateFleetRequest(
    @SerializedName("total_ambulances") val totalAmbulances: Int? = null,
    @SerializedName("available_ambulances") val availableAmbulances: Int? = null
)

data class HospitalStats(
    @SerializedName("hospital_name") val hospitalName: String = "",
    @SerializedName("total_emergencies") val totalEmergencies: Int = 0,
    @SerializedName("pending_emergencies") val pendingEmergencies: Int = 0,
    @SerializedName("active_dispatches") val activeDispatches: Int = 0,
    @SerializedName("admitted_patients") val admittedPatients: Int = 0,
    @SerializedName("total_ambulances") val totalAmbulances: Int = 5,
    @SerializedName("available_ambulances") val availableAmbulances: Int = 5
)
