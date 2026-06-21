package com.chama.groupmoneymanager.data.requests

import kotlinx.serialization.Serializable

@Serializable
data class CreateNestRequest(
    val name: String,
    val contributionAmount: Double
)

@Serializable
data class JoinNestRequest(
    val inviteCode: String
)