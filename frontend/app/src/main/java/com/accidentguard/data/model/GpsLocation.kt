package com.accidentguard.data.model

import com.google.gson.annotations.SerializedName

data class GpsLocation(
    @SerializedName("id") val id: Int,
    @SerializedName("vehicle_id") val vehicleId: Int,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("speed") val speed: Float,
    @SerializedName("timestamp") val timestamp: String
)
