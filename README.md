# Mfuko — Backend (Ktor Server)

> *Mfuko* is Swahili for **fund / wallet**. This is the optional Ktor backend for the [Mfuko Android app](https://github.com/cypher-30/Mfuko) — a group savings & loans (chama) manager.

The Android app is **offline-first** and works fully without this server (Room DB is its source of truth). This backend exists to eventually enable real multi-device sync, M-Pesa Daraja integration, and cloud push notifications.

**Status: work in progress, not yet runnable end-to-end.** See [Known issues](#known-issues) below before trying to wire it up to the app.

---

## Tech stack

| Component | Library |
|---|---|
| Server framework | Ktor 3.2.3 (Netty engine) |
| Database ORM | JetBrains Exposed 0.49.0 |
| Database | MySQL 8.4 (local) |
| Auth | JWT (auth0 java-jwt 4.4.0) |
| Password hashing | BCrypt (jbcrypt 0.4) |
| Connection pool | HikariCP 5.1.0 |
| Serialisation | Gson (ktor-serialization-gson) |

---

## Running locally

### Prerequisites
1. MySQL 8.x installed and running.
2. Create the database: `CREATE DATABASE mfuko_db;`
3. Set your real MySQL password in `src/main/resources/application.conf` (`database.password`).

```bash
./gradlew run
# Server starts at http://0.0.0.0:8081
```

### Connecting the Android app
The app's `BuildConfig.BASE_URL` defaults to `http://10.0.2.2:8081/` (Android emulator's loopback to the host machine) and `BuildConfig.USE_REMOTE = false` by default — the app runs fully offline unless that flag is flipped. See the app repo's `docs/BACKEND.md` for the full sync plan.

---

## API endpoints (current)

### Auth (no JWT required)
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/register` | `{name, phone, password}` | `{userId, name, phone, token}` |
| POST | `/api/auth/login` | `{phone, password}` | `{userId, name, phone, token}` |

> Login uses **phone number** (not email). Password minimum 8 characters.

### Nests (JWT required)
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/nests/create` | `{name, contributionAmount}` | `{nestId, nestName, inviteCode}` |
| POST | `/api/nests/join` | `{inviteCode}` | `{nestId, nestName, inviteCode}` |
| GET | `/api/nests/{nestId}/members` | — | `[{userId, name, role, amountPaid, totalDue, status}]` |

### Contributions (JWT required)
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/contributions/record` | `{nestId, userId, amount}` | 200 OK |

### Loans (JWT required)
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/loans/request` | `{nestId, amount, termMonths}` | `{loanId, nestId, userId, amount, status}` |
| POST | `/api/loans/{loanId}/approve` | — | 200 OK |
| POST | `/api/loans/{loanId}/reject` | — | 200 OK |
| POST | `/api/loans/{loanId}/repay` | `{amount}` | 200 OK |
| GET | `/api/loans/nest/{nestId}` | — | `[{loanId, userId, principalAmount, termMonths, status}]` |

### Dashboard (JWT required)
| Method | Path | Response |
|---|---|---|
| GET | `/api/me/dashboard` | `{contributionStatus?, loanStatus?, penaltyStatus?}` |

---

## Known issues

This backend doesn't run/compile cleanly yet. In rough priority order:

1. **Ktor version conflict.** `build.gradle.kts` mixes Ktor 3.2.3 (most deps, via the version catalog) with a hardcoded Ktor 2.3.12 JWT auth dependency — these are binary-incompatible. Fix: bump `ktor-server-auth-jwt-jvm` to 3.2.3.
2. **`Routing.kt` references non-existent table columns.** `/api/nests/create` references `NestsTable.contributionAmount` and `NestsTable.inviteCode`, neither of which exist on `NestsTable` (it has `joinCode`, not `inviteCode`; contribution amount belongs on `CyclesTable`).
3. **`NestsTable.managerId` (non-nullable) is never set on create** — every create-nest call will hit a constraint violation.
4. **`CyclesTable.startDate`/`endDate` type mismatch** — the table is `date(...)` (`LocalDate`) but routes insert `LocalDateTime.now()`.
5. **Loan list path mismatch** — the app calls `GET /api/nests/{nestId}/loans`, this server serves `GET /api/loans/nest/{nestId}`.
6. **`DashboardResponse` is missing `userRole`** — the app's DTO expects it; without it, manager-only UI never activates on the app side.
7. **JWT secret and DB credentials are hardcoded in `application.conf`** — fine for local dev, must move to environment variables before any real deployment.

## Roadmap

- Fix all of the above.
- Replace MySQL with an embedded H2 file database so `./gradlew run` needs zero external services for local dev.
- Deploy (Railway/Render) with Postgres + real M-Pesa Daraja + FCM push, and flip `BuildConfig.USE_REMOTE = true` on the Android side.
