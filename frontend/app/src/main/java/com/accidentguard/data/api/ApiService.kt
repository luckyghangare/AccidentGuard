package com.accidentguard.data.api

import com.accidentguard.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth (User)
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<User>>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginData>>

    @GET("api/auth/me")
    suspend fun getMe(@Header("Authorization") authHeader: String): Response<ApiResponse<User>>

    @POST("api/auth/logout")
    suspend fun logout(@Header("Authorization") authHeader: String): Response<ApiResponse<Unit>>

    // Vehicles
    @GET("api/vehicles")
    suspend fun getVehicles(@Header("Authorization") authHeader: String): Response<ApiResponse<List<Vehicle>>>

    @GET("api/vehicles/{id}")
    suspend fun getVehicleDetail(
        @Header("Authorization") authHeader: String,
        @Path("id") vehicleId: Int
    ): Response<ApiResponse<Vehicle>>

    // GPS Location
    @GET("api/vehicles/{id}/location")
    suspend fun getLatestLocation(
        @Header("Authorization") authHeader: String,
        @Path("id") vehicleId: Int
    ): Response<ApiResponse<GpsLocation>>

    // Sensors
    @GET("api/vehicles/{id}/sensors/latest")
    suspend fun getLatestSensorReading(
        @Header("Authorization") authHeader: String,
        @Path("id") vehicleId: Int
    ): Response<ApiResponse<SensorReading>>

    // Accidents
    @GET("api/accidents")
    suspend fun getAccidentHistory(@Header("Authorization") authHeader: String): Response<ApiResponse<List<Accident>>>

    @PUT("api/accidents/{id}/cancel")
    suspend fun cancelAccidentAlert(
        @Header("Authorization") authHeader: String,
        @Path("id") accidentId: Int
    ): Response<ApiResponse<Accident>>

    // Emergency Contacts
    @GET("api/contacts")
    suspend fun getContacts(@Header("Authorization") authHeader: String): Response<ApiResponse<List<EmergencyContact>>>

    @POST("api/contacts")
    suspend fun addContact(
        @Header("Authorization") authHeader: String,
        @Body request: ContactRequest
    ): Response<ApiResponse<EmergencyContact>>

    @PUT("api/contacts/{id}")
    suspend fun updateContact(
        @Header("Authorization") authHeader: String,
        @Path("id") contactId: Int,
        @Body request: ContactRequest
    ): Response<ApiResponse<EmergencyContact>>

    @DELETE("api/contacts/{id}")
    suspend fun deleteContact(
        @Header("Authorization") authHeader: String,
        @Path("id") contactId: Int
    ): Response<ApiResponse<Unit>>

    // Vehicles - Add
    @POST("api/vehicles")
    suspend fun addVehicle(
        @Header("Authorization") authHeader: String,
        @Body request: VehicleRequest
    ): Response<ApiResponse<Vehicle>>

    // Profile update
    @PUT("api/auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") authHeader: String,
        @Body request: UpdateProfileRequest
    ): Response<ApiResponse<User>>

    // ── Hospital Auth & Operations ──
    @POST("api/hospitals/login")
    suspend fun hospitalLogin(@Body request: HospitalLoginRequest): Response<ApiResponse<HospitalLoginData>>

    @POST("api/hospitals/register")
    suspend fun hospitalRegister(@Body request: HospitalRegisterRequest): Response<ApiResponse<HospitalLoginData>>

    @GET("api/hospitals/me")
    suspend fun getHospitalProfile(@Header("Authorization") authHeader: String): Response<ApiResponse<Hospital>>

    @GET("api/hospitals/alerts")
    suspend fun getHospitalAlerts(@Header("Authorization") authHeader: String): Response<ApiResponse<List<EmergencyAlert>>>

    @GET("api/hospitals/alerts/{id}")
    suspend fun getHospitalAlertDetail(
        @Header("Authorization") authHeader: String,
        @Path("id") alertId: Int
    ): Response<ApiResponse<EmergencyAlert>>

    @POST("api/hospitals/alerts/{id}/dispatch")
    suspend fun dispatchAmbulance(
        @Header("Authorization") authHeader: String,
        @Path("id") alertId: Int,
        @Body request: AmbulanceDispatchRequest
    ): Response<ApiResponse<EmergencyAlert>>

    @POST("api/hospitals/alerts/{id}/admit")
    suspend fun admitPatient(
        @Header("Authorization") authHeader: String,
        @Path("id") alertId: Int,
        @Body notes: Map<String, String> = emptyMap()
    ): Response<ApiResponse<EmergencyAlert>>

    @GET("api/hospitals/stats")
    suspend fun getHospitalStats(@Header("Authorization") authHeader: String): Response<ApiResponse<HospitalStats>>

    @PUT("api/hospitals/fleet")
    suspend fun updateHospitalFleet(
        @Header("Authorization") authHeader: String,
        @Body request: UpdateFleetRequest
    ): Response<ApiResponse<Hospital>>
}
