package com.accidentguard.data.repository

import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.ContactRequest
import com.accidentguard.data.model.EmergencyContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactRepository {
    private val api = RetrofitClient.apiService

    suspend fun getContacts(token: String): Result<List<EmergencyContact>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getContacts("Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.data ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch contacts"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun addContact(token: String, name: String, phone: String, rel: String, isPrimary: Boolean): Result<EmergencyContact> = withContext(Dispatchers.IO) {
        try {
            val response = api.addContact("Bearer $token", ContactRequest(name, phone, rel, isPrimary))
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to add contact"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun updateContact(token: String, contactId: Long, name: String, phone: String, rel: String, isPrimary: Boolean): Result<EmergencyContact> = withContext(Dispatchers.IO) {
        try {
            val response = api.updateContact("Bearer $token", contactId, ContactRequest(name, phone, rel, isPrimary))
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to update contact"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun deleteContact(token: String, contactId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteContact("Bearer $token", contactId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete contact"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getReceivedInvitations(token: String): Result<List<EmergencyContact>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getReceivedInvitations("Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.data ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch invitations"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun acceptInvitation(token: String, invitationId: Long): Result<EmergencyContact> = withContext(Dispatchers.IO) {
        try {
            val response = api.acceptInvitation("Bearer $token", invitationId)
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to accept invitation"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun rejectInvitation(token: String, invitationId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.rejectInvitation("Bearer $token", invitationId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to reject invitation"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}
