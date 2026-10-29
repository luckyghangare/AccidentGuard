package com.accidentguard.data.model

import com.google.gson.annotations.SerializedName

data class EmergencyContact(
    @SerializedName("id") val id: Long,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("relationship_type") val relationshipType: String,
    @SerializedName("is_primary") val isPrimary: Boolean
)

data class ContactRequest(
    @SerializedName("name") val name: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("relationship_type") val relationshipType: String = "Family",
    @SerializedName("is_primary") val isPrimary: Boolean = false
)
