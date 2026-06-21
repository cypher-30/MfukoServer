package com.chama.groupmoneymanager.data.requests

data class LoanRequestRequest(
    val nestId: Long,
    val amount: Double,
    val termMonths: Int
)

// ADD THIS NEW DATA CLASS
data class RepayLoanRequest(
    val amount: Double
)