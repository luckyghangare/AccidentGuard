package com.accidentguard.data.model

import com.google.gson.annotations.SerializedName

data class AccidentCreateRequest(
    @SerializedName("vehicle_id") val vehicleId: Long,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("impact_g") val impactG: Float,
    @SerializedName("date_str") val dateStr: String? = null,
    @SerializedName("time_str") val timeStr: String? = null
)
