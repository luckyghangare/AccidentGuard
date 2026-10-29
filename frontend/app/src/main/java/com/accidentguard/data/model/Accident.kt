package com.accidentguard.data.model

import com.google.gson.annotations.SerializedName

data class Accident(
    @SerializedName("id") val id: Long,
    @SerializedName("vehicle_id") val vehicleId: Long,
    @SerializedName("date_str") val dateStr: String,
    @SerializedName("time_str") val timeStr: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("impact_g") val impactG: Float,
    @SerializedName("status") val status: String,
    @SerializedName("timestamp") val timestamp: String,
    val source: String = "backend"
)
