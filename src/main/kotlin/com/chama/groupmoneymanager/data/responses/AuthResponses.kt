package com.chama.groupmoneymanager.data.responses

// This is the data we send back to the Android app on a successful login/registration
data class AuthResponse(
    val userId: Long,
    val name: String,
    val phone: String,
    val token: String
)