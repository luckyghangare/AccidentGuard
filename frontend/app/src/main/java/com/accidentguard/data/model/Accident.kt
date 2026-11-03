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
    @SerializedName("call_session_id") val callSessionId: Int? = null,
    @SerializedName("livekit_token") val livekitToken: String? = null,
    @SerializedName("livekit_url") val livekitUrl: String? = null,
    val source: String = "backend"
)
