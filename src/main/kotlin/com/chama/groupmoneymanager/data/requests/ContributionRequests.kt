package com.chama.groupmoneymanager.data.requests

import kotlinx.serialization.Serializable

@Serializable
data class RecordContributionRequest(
    val nestId: Long,
    val userId: Long,
    val amount: Double
)