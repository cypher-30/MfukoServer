package com.chama.mfuko.data.responses

// Using "Dto" suffix to distinguish from potential future domain models
data class ContributionStatusDto(
    val amountDue: Double,
    val amountPaid: Double,
    val dueDate: String
)

data class LoanStatusDto(
    val outstandingBalance: Double,
    val nextDueDate: String? // A loan might not have a next due date if fully paid
)

data class PenaltyStatusDto(
    val totalUnpaid: Double
)

// The main response object that combines everything
data class DashboardResponse(
    val contributionStatus: ContributionStatusDto?,
    val loanStatus: LoanStatusDto?,
    val penaltyStatus: PenaltyStatusDto?
)