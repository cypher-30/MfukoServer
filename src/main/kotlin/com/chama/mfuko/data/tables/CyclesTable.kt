package com.chama.mfuko.data.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.date

object CyclesTable : LongIdTable("cycles") {
    val nestId = long("nest_id").references(NestsTable.id)
    val name = varchar("name", 255)
    val startDate = date("start_date")
    val endDate = date("end_date")
    val amountDuePerMember = decimal("amount_due_per_member", 12, 2)
    val status = varchar("status", 50) // "open" or "closed"
}