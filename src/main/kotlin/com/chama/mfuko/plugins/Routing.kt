package com.chama.mfuko.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.chama.mfuko.data.requests.*
import com.chama.mfuko.data.responses.*
import com.chama.mfuko.data.tables.*
import com.chama.mfuko.db.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

fun Application.configureRouting() {
    val secret = environment.config.property("jwt.secret").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()

    routing {
        route("/api/auth") {
            post("/register") {
                val request = call.receive<RegisterRequest>()
                if (request.password.length < 8) {
                    call.respond(HttpStatusCode.BadRequest, "Password must be at least 8 characters long.")
                    return@post
                }
                val hashedPassword = BCrypt.hashpw(request.password, BCrypt.gensalt())
                val newUserId = dbQuery {
                    UsersTable.insertAndGetId {
                        it[name] = request.name
                        it[phone] = request.phone
                        it[passwordHash] = hashedPassword
                        it[createdAt] = Instant.now()
                    }
                }
                val token = JWT.create()
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .withClaim("userId", newUserId.value)
                    .withExpiresAt(Date(System.currentTimeMillis() + 60_000_000))
                    .sign(Algorithm.HMAC256(secret))
                call.respond(
                    status = HttpStatusCode.Created,
                    message = AuthResponse(
                        userId = newUserId.value,
                        name = request.name,
                        phone = request.phone,
                        token = token
                    )
                )
            }
            post("/login") {
                val request = call.receive<LoginRequest>()
                val userRow = dbQuery {
                    UsersTable.selectAll().where { UsersTable.phone eq request.phone }.singleOrNull()
                }
                if (userRow == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid phone number or password"))
                    return@post
                }
                val storedPasswordHash = userRow[UsersTable.passwordHash]
                val passwordsMatch = BCrypt.checkpw(request.password, storedPasswordHash)
                if (!passwordsMatch) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid phone number or password"))
                    return@post
                }
                val userId = userRow[UsersTable.id].value
                val token = JWT.create()
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .withClaim("userId", userId)
                    .withExpiresAt(Date(System.currentTimeMillis() + 60_000_000))
                    .sign(Algorithm.HMAC256(secret))
                call.respond(
                    status = HttpStatusCode.OK,
                    message = AuthResponse(
                        userId = userId,
                        name = userRow[UsersTable.name],
                        phone = userRow[UsersTable.phone],
                        token = token
                    )
                )
            }
        }

        // ✅ UPDATED ROUTE BLOCK
        route("/api/nests") {
            authenticate("auth-jwt") {
                post("/create") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal!!.payload.getClaim("userId").asLong()
                    val request = call.receive<CreateNestRequest>()

                    // Create a unique 6-digit invite code
                    val inviteCode = (100000..999999).random().toString()

                    val newNestId = dbQuery {
                        NestsTable.insertAndGetId {
                            it[name] = request.name
                            it[contributionAmount] = request.contributionAmount.toBigDecimal()
                            it[NestsTable.inviteCode] = inviteCode
                        }
                    }

                    // Make the creator the manager
                    dbQuery {
                        MembershipsTable.insert {
                            it[MembershipsTable.userId] = userId
                            it[nestId] = newNestId.value
                            it[role] = "manager"
                        }
                    }

                    // Create the very first contribution cycle for the new nest
                    val currentDate = LocalDateTime.now()
                    dbQuery {
                        CyclesTable.insert {
                            it[nestId] = newNestId.value
                            it[startDate] = currentDate
                            it[endDate] = currentDate.plusMonths(1) // Cycle ends in 1 month
                            it[amountDuePerMember] = request.contributionAmount.toBigDecimal()
                            it[status] = "open"
                        }
                    }

                    call.respond(HttpStatusCode.Created, NestResponse(newNestId.value, request.name, inviteCode))
                }

                post("/join") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal!!.payload.getClaim("userId").asLong()
                    val request = call.receive<JoinNestRequest>()

                    val nest = dbQuery {
                        NestsTable.selectAll().where { NestsTable.inviteCode eq request.inviteCode }.singleOrNull()
                    }

                    if (nest == null) {
                        call.respond(HttpStatusCode.NotFound, "Nest with this invite code not found.")
                        return@post
                    }

                    val nestId = nest[NestsTable.id].value

                    val existingMembership = dbQuery {
                        MembershipsTable.selectAll().where {
                            (MembershipsTable.userId eq userId) and (MembershipsTable.nestId eq nestId)
                        }.count() > 0
                    }

                    if (existingMembership) {
                        call.respond(HttpStatusCode.Conflict, "You are already a member of this nest.")
                        return@post
                    }

                    dbQuery {
                        MembershipsTable.insert {
                            it[MembershipsTable.userId] = userId
                            it[MembershipsTable.nestId] = nestId
                            it[role] = "member"
                        }
                    }

                    call.respond(HttpStatusCode.OK, NestResponse(nestId, nest[NestsTable.name], request.inviteCode))
                }
            }
        }

        route("/api/nests/{nestId}/members") {
            authenticate("auth-jwt") {
                get {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal!!.payload.getClaim("userId").asLong()
                    val nestIdParam = call.parameters["nestId"]?.toLongOrNull()
                    if (nestIdParam == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid Nest ID")
                        return@get
                    }
                    val isMember = dbQuery {
                        MembershipsTable.selectAll().where {
                            (MembershipsTable.userId eq userId) and (MembershipsTable.nestId eq nestIdParam)
                        }.count() > 0
                    }
                    if (!isMember) {
                        call.respond(HttpStatusCode.Forbidden, "You are not a member of this nest.")
                        return@get
                    }
                    val openCycle = dbQuery {
                        CyclesTable.selectAll()
                            .where { (CyclesTable.nestId eq nestIdParam) and (CyclesTable.status eq "open") }
                            .orderBy(CyclesTable.endDate, SortOrder.DESC).limit(1).singleOrNull()
                    }
                    val members = dbQuery {
                        (UsersTable innerJoin MembershipsTable)
                            .selectAll().where { MembershipsTable.nestId eq nestIdParam }
                            .map { row ->
                                val memberId = row[UsersTable.id].value
                                val contribution = openCycle?.let { cycle ->
                                    ContributionsTable.selectAll().where {
                                        (ContributionsTable.cycleId eq cycle[CyclesTable.id].value) and (ContributionsTable.userId eq memberId)
                                    }.singleOrNull()
                                }
                                MemberStatusDto(
                                    userId = memberId,
                                    name = row[UsersTable.name],
                                    role = row[MembershipsTable.role],
                                    amountPaid = contribution?.get(ContributionsTable.amountPaid)?.toDouble() ?: 0.0,
                                    totalDue = openCycle?.get(CyclesTable.amountDuePerMember)?.toDouble() ?: 0.0,
                                    status = contribution?.get(ContributionsTable.status) ?: "N/A"
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, members)
                }
            }
        }

        route("/api/contributions") {
            authenticate("auth-jwt") {
                post("/record") {
                    val principal = call.principal<JWTPrincipal>()
                    val managerId = principal!!.payload.getClaim("userId").asLong()
                    val request = call.receive<RecordContributionRequest>()

                    // Basic validation: Ensure the person making the request is a manager
                    val managerMembership = dbQuery {
                        MembershipsTable.selectAll().where {
                            (MembershipsTable.userId eq managerId) and (MembershipsTable.nestId eq request.nestId)
                        }.singleOrNull()
                    }

                    if (managerMembership == null || managerMembership[MembershipsTable.role] != "manager") {
                        call.respond(HttpStatusCode.Forbidden, "You are not authorized to perform this action.")
                        return@post
                    }

                    // Find the current open cycle for the nest
                    val openCycle = dbQuery {
                        CyclesTable.selectAll()
                            .where { (CyclesTable.nestId eq request.nestId) and (CyclesTable.status eq "open") }
                            .orderBy(CyclesTable.endDate, SortOrder.DESC).limit(1).singleOrNull()
                    }

                    if (openCycle == null) {
                        call.respond(HttpStatusCode.NotFound, "No open contribution cycle found for this nest.")
                        return@post
                    }

                    val cycleId = openCycle[CyclesTable.id].value

                    // Find if a contribution record already exists for this user in this cycle
                    val existingContribution = dbQuery {
                        ContributionsTable.selectAll().where {
                            (ContributionsTable.cycleId eq cycleId) and (ContributionsTable.userId eq request.userId)
                        }.singleOrNull()
                    }

                    dbQuery {
                        if (existingContribution == null) {
                            // Create a new contribution record
                            ContributionsTable.insert {
                                it[ContributionsTable.userId] = request.userId
                                it[ContributionsTable.cycleId] = cycleId
                                it[amountPaid] = request.amount.toBigDecimal()
                                it[datePaid] = LocalDateTime.now()
                            }
                        } else {
                            // Update the existing record by adding the new amount
                            val currentAmount = existingContribution[ContributionsTable.amountPaid]
                            ContributionsTable.update({
                                (ContributionsTable.cycleId eq cycleId) and (ContributionsTable.userId eq request.userId)
                            }) {
                                it[amountPaid] = currentAmount + request.amount.toBigDecimal()
                                it[datePaid] = LocalDateTime.now()
                            }
                        }
                    }

                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        route("/api/loans") {
            authenticate("auth-jwt") {
                post("/request") {
                    val request = call.receive<LoanRequestRequest>()
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal!!.payload.getClaim("userId").asLong()
                    val isMember = dbQuery {
                        MembershipsTable.selectAll().where {
                            (MembershipsTable.userId eq userId) and (MembershipsTable.nestId eq request.nestId)
                        }.count() > 0
                    }
                    if (!isMember) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You are not a member of this nest."))
                        return@post
                    }
                    val existingLoan = dbQuery {
                        LoansTable.selectAll().where {
                            (LoansTable.userId eq userId) and (LoansTable.status inList listOf("pending", "active"))
                        }.count() > 0
                    }
                    if (existingLoan) {
                        call.respond(HttpStatusCode.Conflict, mapOf("error" to "You already have a pending or active loan."))
                        return@post
                    }
                    val newLoanId = dbQuery {
                        LoansTable.insertAndGetId {
                            it[nestId] = request.nestId
                            it[this.userId] = userId
                            it[principalAmount] = request.amount.toBigDecimal()
                            it[interestRate] = 10.0.toBigDecimal()
                            it[interestType] = "reducing"
                            it[termMonths] = request.termMonths
                            it[status] = "pending"
                            it[outstandingBalance] = request.amount.toBigDecimal()
                            it[requestDate] = LocalDateTime.now()
                        }
                    }
                    call.respond(HttpStatusCode.Created, LoanResponse(newLoanId.value, request.nestId, userId, request.amount, "pending"))
                }

                post("/{loanId}/approve") {
                    val principal = call.principal<JWTPrincipal>()
                    val managerId = principal!!.payload.getClaim("userId").asLong()
                    val loanId = call.parameters["loanId"]?.toLongOrNull()
                    if (loanId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid Loan ID")
                        return@post
                    }
                    val loanAndNest = dbQuery {
                        (LoansTable innerJoin NestsTable)
                            .select(LoansTable.status, NestsTable.managerId)
                            .where { LoansTable.id eq loanId }
                            .singleOrNull()
                    }
                    if (loanAndNest == null) {
                        call.respond(HttpStatusCode.NotFound, "Loan not found")
                        return@post
                    }
                    if (loanAndNest[NestsTable.managerId] != managerId) {
                        call.respond(HttpStatusCode.Forbidden, "You are not authorized to approve this loan.")
                        return@post
                    }
                    if (loanAndNest[LoansTable.status] != "pending") {
                        call.respond(HttpStatusCode.Conflict, "This loan is not in a pending state.")
                        return@post
                    }
                    dbQuery {
                        LoansTable.update({ LoansTable.id eq loanId }) {
                            it[status] = "active"
                            it[approvalDate] = LocalDateTime.now()
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Loan approved successfully."))
                }

                post("/{loanId}/reject") {
                    val principal = call.principal<JWTPrincipal>()
                    val managerId = principal!!.payload.getClaim("userId").asLong()
                    val loanId = call.parameters["loanId"]?.toLongOrNull()

                    if (loanId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid loan ID")
                        return@post
                    }

                    // Verify the user is a manager of the correct nest
                    val loanAndNest = dbQuery {
                        (LoansTable innerJoin NestsTable)
                            .select(NestsTable.managerId)
                            .where { LoansTable.id eq loanId }
                            .singleOrNull()
                    }

                    if (loanAndNest == null) {
                        call.respond(HttpStatusCode.NotFound, "Loan not found")
                        return@post
                    }

                    if (loanAndNest[NestsTable.managerId] != managerId) {
                        call.respond(HttpStatusCode.Forbidden, "You are not authorized to reject this loan.")
                        return@post
                    }

                    dbQuery {
                        LoansTable.update({ LoansTable.id eq loanId }) {
                            it[status] = "rejected"
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Loan rejected successfully."))
                }

                post("/{loanId}/repay") {
                    val request = call.receive<RepayLoanRequest>()
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal!!.payload.getClaim("userId").asLong()
                    val loanId = call.parameters["loanId"]?.toLongOrNull()

                    if (loanId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid loan ID")
                        return@post
                    }

                    val loan = dbQuery {
                        LoansTable.selectAll().where { LoansTable.id eq loanId }.singleOrNull()
                    }

                    if (loan == null) {
                        call.respond(HttpStatusCode.NotFound, "Loan not found")
                        return@post
                    }

                    if (loan[LoansTable.userId] != userId) {
                        call.respond(HttpStatusCode.Forbidden, "You are not authorized to repay this loan.")
                        return@post
                    }

                    if (loan[LoansTable.status] != "active") {
                        call.respond(HttpStatusCode.Conflict, "This loan is not active.")
                        return@post
                    }

                    dbQuery {
                        val currentBalance = loan[LoansTable.outstandingBalance]
                        val newBalance = currentBalance - request.amount.toBigDecimal()
                        val newStatus = if (newBalance.toDouble() <= 0) "paid" else "active"

                        LoansTable.update({ LoansTable.id eq loanId }) {
                            it[outstandingBalance] = newBalance
                            it[status] = newStatus
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Repayment successful."))
                }


                get("/nest/{nestId}") {
                    val principal = call.principal<JWTPrincipal>()
                    val managerId = principal!!.payload.getClaim("userId").asLong()
                    val nestId = call.parameters["nestId"]?.toLongOrNull()
                    if (nestId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid Nest ID")
                        return@get
                    }
                    val nest = dbQuery {
                        NestsTable.selectAll().where { NestsTable.id eq nestId }.singleOrNull()
                    }
                    if (nest == null || nest[NestsTable.managerId] != managerId) {
                        call.respond(HttpStatusCode.Forbidden, "You are not the manager of this nest.")
                        return@get
                    }
                    val loans = dbQuery {
                        LoansTable.selectAll().where { LoansTable.nestId eq nestId }.map {
                            LoanDetailsResponse(
                                loanId = it[LoansTable.id].value,
                                nestId = it[LoansTable.nestId],
                                userId = it[LoansTable.userId],
                                principalAmount = it[LoansTable.principalAmount].toDouble(),
                                interestRate = it[LoansTable.interestRate].toDouble(),
                                termMonths = it[LoansTable.termMonths],
                                outstandingBalance = it[LoansTable.outstandingBalance].toDouble(),
                                status = it[LoansTable.status],
                                requestDate = it[LoansTable.requestDate].toString(),
                                approvalDate = it[LoansTable.approvalDate]?.toString()
                            )
                        }
                    }
                    call.respond(HttpStatusCode.OK, loans)
                }
            }
        }

        route("/api/me") {
            authenticate("auth-jwt") {
                get("/dashboard") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal!!.payload.getClaim("userId").asLong()
                    val membership = dbQuery {
                        MembershipsTable.selectAll().where { MembershipsTable.userId eq userId }.singleOrNull()
                    }
                    if (membership == null) {
                        call.respond(HttpStatusCode.OK, DashboardResponse(null, null, null))
                        return@get
                    }
                    val nestId = membership[MembershipsTable.nestId]

                    val contributionStatus = dbQuery {
                        val openCycle = CyclesTable.selectAll()
                            .where { (CyclesTable.nestId eq nestId) and (CyclesTable.status eq "open") }
                            .orderBy(CyclesTable.endDate, SortOrder.DESC).limit(1).singleOrNull()

                        if (openCycle == null) {
                            null
                        } else {
                            val contribution = ContributionsTable.selectAll().where {
                                (ContributionsTable.cycleId eq openCycle[CyclesTable.id].value) and (ContributionsTable.userId eq userId)
                            }.singleOrNull()

                            ContributionStatusDto(
                                amountDue = openCycle[CyclesTable.amountDuePerMember].toDouble(),
                                amountPaid = contribution?.get(ContributionsTable.amountPaid)?.toDouble() ?: 0.0,
                                dueDate = openCycle[CyclesTable.endDate].toString()
                            )
                        }
                    }

                    val loanStatus = dbQuery {
                        val activeLoan = LoansTable.selectAll().where {
                            (LoansTable.userId eq userId) and (LoansTable.status eq "active")
                        }.singleOrNull()
                        activeLoan?.let {
                            LoanStatusDto(
                                loanId = it[LoansTable.id].value,
                                outstandingBalance = it[LoansTable.outstandingBalance].toDouble(),
                                nextDueDate = null
                            )
                        }
                    }

                    val penaltyStatus = dbQuery {
                        val sumExpression = PenaltiesTable.amount.sum()
                        val totalUnpaid = PenaltiesTable
                            .select(sumExpression)
                            .where { (PenaltiesTable.userId eq userId) and (PenaltiesTable.status eq "unpaid") }
                            .singleOrNull()?.get(sumExpression)?.toDouble() ?: 0.0

                        PenaltyStatusDto(totalUnpaid = totalUnpaid)
                    }
                    val dashboardResponse = DashboardResponse(
                        contributionStatus = contributionStatus,
                        loanStatus = loanStatus,
                        penaltyStatus = penaltyStatus
                    )
                    call.respond(HttpStatusCode.OK, dashboardResponse)
                }
            }
        }
    }
}
