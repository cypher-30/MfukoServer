package com.chama.groupmoneymanager.data.requests

data class RegisterRequest(
    val name: String,
    val phone: String,
    val password: String
)

// ADD THIS NEW DATA CLASS
data class LoginRequest(
    val phone: String,
    val password: String
)