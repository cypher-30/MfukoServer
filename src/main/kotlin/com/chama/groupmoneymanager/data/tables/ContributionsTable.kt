package com.chama.groupmoneymanager.data.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object ContributionsTable : LongIdTable("contributions") {
    val cycleId = long("cycle_id").references(CyclesTable.id)
    val userId = long("user_id").references(UsersTable.id)
    val amountPaid = decimal("amount_paid", 12, 2)
    val status = varchar("status", 50) // "unpaid", "paid", "late"
    val paidAt = datetime("paid_at").nullable()
    val notes = text("notes").nullable()
}