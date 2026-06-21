package com.chama.mfuko.data.responses

data class MemberStatusDto(
    val userId: Long,
    val name: String,
    val role: String, // <-- THIS IS THE NEW FIELD
    val amountPaid: Double,
    val totalDue: Double,
    val status: String
)