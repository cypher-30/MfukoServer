# Mfuko — Backend (Ktor Server)

> *Mfuko* is Swahili for **fund / wallet**. This is the optional Ktor backend for the [Mfuko Android app](https://github.com/cypher-30/Mfuko) — a group savings & loans (chama) manager.

The Android app is **offline-first** and works fully without this server (Room DB is its source of truth). This backend exists to eventually enable real multi-device sync, M-Pesa Daraja integration, and cloud push notifications.

**Status: runs and compiles cleanly for the routes below.** See [Still open](#still-open) for what's left before flipping the app to remote mode.

---

## Tech stack

| Component | Library |
|---|---|
| Server framework | Ktor 3.2.3 (Netty engine) |
| Database ORM | JetBrains Exposed 0.49.0 |
| Database | H2 file (local, zero-setup) |
| Auth | JWT (auth0 java-jwt 4.4.0) |
| Password hashing | BCrypt (jbcrypt 0.4) |
| Connection pool | HikariCP 5.1.0 |
| Serialisation | Gson (ktor-serialization-gson) |

---

## Running locally

```bash
./gradlew run
# Server starts at http://0.0.0.0:8081
# DB is an H2 file at ./build/mfuko_db — created automatically, no setup needed.
```

### Connecting the Android app
The app's `BuildConfig.BASE_URL` points at a LAN IP (e.g. `http://192.168.x.x:8081/`) for
a physical device, or `http://10.0.2.2:8081/` for the Android emulator's loopback to the
host machine. `BuildConfig.USE_REMOTE = false` by default — the app runs fully offline
unless that flag is flipped. See the app repo's `docs/BACKEND.md` for the full sync plan.

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
| GET | `/api/me/dashboard` | `{contributionStatus?, loanStatus?, penaltyStatus?, userRole?}` |

---

## Still open

- **JWT secret and DB credentials are hardcoded in `application.conf`** — fine for local dev, must move to environment variables before any real deployment.
- **`BuildConfig.USE_REMOTE` is still `false` on the Android side.** The app's DI
  layer supports switching between local and network repos via this flag, but
  a few screens still read Room directly regardless of it — don't flip it
  until those are migrated (see the app repo's `docs/BACKEND.md`).

## Roadmap

- Externalise JWT secret / DB config to env vars.
- Finish migrating the remaining Room-only screens on the Android side, then flip `BuildConfig.USE_REMOTE = true`.
- Deploy (Railway/Render) with Postgres + real M-Pesa Daraja + FCM push.
