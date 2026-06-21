package com.chama.groupmoneymanager.data.responses

// This is the original response for a new request
data class LoanResponse(
    val loanId: Long,
    val nestId: Long,
    val userId: Long,
    val amount: Double,
    val status: String
)

// ADD THIS NEW, MORE DETAILED RESPONSE CLASS
data class LoanDetailsResponse(
    val loanId: Long,
    val nestId: Long,
    val userId: Long,
    val principalAmount: Double,
    val interestRate: Double,
    val termMonths: Int,
    val outstandingBalance: Double,
    val status: String,
    val requestDate: String,
    val approvalDate: String?
)