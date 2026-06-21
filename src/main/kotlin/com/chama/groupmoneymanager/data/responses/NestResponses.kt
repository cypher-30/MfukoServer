package com.chama.groupmoneymanager.data.responses

import kotlinx.serialization.Serializable

@Serializable
data class NestResponse(
    val nestId: Long,
    val nestName: String,
    val inviteCode: String
)