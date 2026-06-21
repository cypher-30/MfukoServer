package com.chama.mfuko.data.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object PenaltiesTable : LongIdTable("penalties") {
    val nestId = long("nest_id").references(NestsTable.id)
    val userId = long("user_id").references(UsersTable.id)
    val amount = decimal("amount", 12, 2)
    val reason = text("reason")
    val status = varchar("status", 50) // "unpaid", "paid", "waived"
    val issuedById = long("issued_by_id").references(UsersTable.id)
    val createdAt = datetime("created_at")
    val paidAt = datetime("paid_at").nullable()
}