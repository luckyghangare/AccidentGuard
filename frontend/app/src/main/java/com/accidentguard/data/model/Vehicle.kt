package com.accidentguard.data.model

import com.google.gson.annotations.SerializedName

data class Vehicle(
    @SerializedName("id") val id: Long,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("vehicle_number") val vehicleNumber: String,
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("status") val status: String,
    @SerializedName("battery_level") val batteryLevel: Int,
    @SerializedName("is_active") val isActive: Boolean
)

data class VehicleRequest(
    @SerializedName("name") val name: String,
    @SerializedName("vehicle_number") val vehicleNumber: String,
    @SerializedName("device_id") val deviceId: String? = null
)
