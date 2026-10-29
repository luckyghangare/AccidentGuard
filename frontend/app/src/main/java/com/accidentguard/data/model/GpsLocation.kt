package com.accidentguard.data.model

import com.google.gson.annotations.SerializedName

data class GpsLocation(
    @SerializedName("id") val id: Long,
    @SerializedName("vehicle_id") val vehicleId: Long,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("speed") val speed: Float,
    @SerializedName("timestamp") val timestamp: String
)
