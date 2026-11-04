package com.accidentguard.data.model

import com.google.gson.annotations.SerializedName

data class EmergencyCallSession(
    @SerializedName("id") val id: Int,
    @SerializedName("accident_id") val accidentId: Int,
    @SerializedName("victim_user_id") val victimUserId: Int,
    @SerializedName("livekit_room_name") val livekitRoomName: String,
    @SerializedName("status") val status: String,
    @SerializedName("winning_contact_user_id") val winningContactUserId: Int?,
    @SerializedName("invitation_started_at") val invitationStartedAt: String?,
    @SerializedName("connected_at") val connectedAt: String?,
    @SerializedName("ended_at") val endedAt: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("participants") val participants: List<CallParticipant> = emptyList()
)

data class CallParticipant(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("participant_role") val participantRole: String,
    @SerializedName("status") val status: String,
    @SerializedName("invited_at") val invitedAt: String,
    @SerializedName("accepted_at") val acceptedAt: String?,
    @SerializedName("joined_at") val joinedAt: String?
)

data class CallAcceptResponse(
    @SerializedName("session") val session: EmergencyCallSession,
    @SerializedName("token") val token: String,
    @SerializedName("livekit_url") val livekitUrl: String
)

data class CallTokenResponse(
    @SerializedName("token") val token: String,
    @SerializedName("room_name") val roomName: String,
    @SerializedName("participant_identity") val participantIdentity: String,
    @SerializedName("livekit_url") val livekitUrl: String
)

data class CallActionRequest(
    @SerializedName("reason") val reason: String? = null
)
