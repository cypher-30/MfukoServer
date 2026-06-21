package com.chama.mfuko.data.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object LoansTable : LongIdTable("loans") {
    val nestId = long("nest_id").references(NestsTable.id)
    val userId = long("user_id").references(UsersTable.id)
    val principalAmount = decimal("principal_amount", 12, 2)
    val interestRate = decimal("interest_rate", 6, 4)
    val interestType = varchar("interest_type", 50) // "flat" or "reducing"
    val termMonths = integer("term_months")
    val status = varchar("status", 50) // "pending", "active", "repaid", etc.
    val totalInterestAmount = decimal("total_interest_amount", 12, 2).nullable()
    val totalRepayableAmount = decimal("total_repayable_amount", 12, 2).nullable()
    val outstandingBalance = decimal("outstanding_balance", 12, 2)
    val requestDate = datetime("request_date")
    val approvalDate = datetime("approval_date").nullable()
    val disbursementDate = date("disbursement_date").nullable()
}