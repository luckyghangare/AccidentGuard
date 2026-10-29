package com.accidentguard.data.model

import com.google.gson.annotations.SerializedName

data class SensorReading(
    @SerializedName("id") val id: Long,
    @SerializedName("vehicle_id") val vehicleId: Long,
    @SerializedName("obstacle_distance") val obstacleDistance: Float,
    @SerializedName("acceleration_g") val accelerationG: Float,
    @SerializedName("gyroscope_rad_s") val gyroscopeRadS: Float,
    @SerializedName("status") val status: String,
    @SerializedName("timestamp") val timestamp: String
)
