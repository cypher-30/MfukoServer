package com.chama.mfuko.data.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object NestsTable : LongIdTable("nests") {
    val name = varchar("name", 255)
    val joinCode = varchar("join_code", 10).uniqueIndex()
    val managerId = long("manager_id").references(UsersTable.id)
    // We'll store complex settings as a simple JSON string in a text field
    val configuration = text("configuration")
    val createdAt = timestamp("created_at")
}