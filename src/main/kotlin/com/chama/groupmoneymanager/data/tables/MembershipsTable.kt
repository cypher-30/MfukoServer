package com.chama.groupmoneymanager.data.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object MembershipsTable : Table("memberships") {
    val userId = long("user_id").references(UsersTable.id)
    val nestId = long("nest_id").references(NestsTable.id)
    val role = varchar("role", 50) // "manager" or "member"
    val createdAt = timestamp("created_at")

    // This creates a composite primary key to ensure a user can only be in a nest once.
    override val primaryKey = PrimaryKey(userId, nestId)
}