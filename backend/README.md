# Splenza — Backend (Phase 1, in progress)

Spring Boot 3 / Java 21 / PostgreSQL / JWT backend for a Splitwise alternative.

## ✅ What's built so far

- **Full database schema** (Flyway `V1__init_schema.sql`) for every Phase 1 table.
- **Auth module (complete):** signup, login, JWT + rotating hashed refresh tokens, forgot/reset password,
  change password, logout. Google Sign-In endpoint stubbed pending OAuth client wiring.
- **Expenses + split engine (complete):** create/edit/delete (soft)/duplicate/get/list, all 4 split types
  (EQUAL, EXACT, PERCENTAGE, SHARES) with cent-accurate rounding — the sum of shares always equals the
  expense total exactly, which is non-negotiable for a ledger. Unit tested.
- **Balance engine (complete):** per-group balances, per-friend balances, dashboard summary (total owed /
  owed to you / net), and Splitwise-style **debt simplification** (greedy largest-creditor/largest-debtor
  matching, extracted into its own `DebtSimplificationService` and unit tested, including the 3-person
  cycle case that should net out to zero transactions).
- **Settlements (complete):** settle up (full or partial), settlement history per group and per friend.
- **Friends (complete):** send/accept/reject requests, remove friend, list, search.
- **Groups (complete):** create (with initial friend invites), edit, archive, soft-delete, invite/remove
  members, leave group. Admin-only actions enforced.
- **Notifications (complete, in-app only):** friend request, group added, expense added/edited, settlement
  recorded — auto-created by the relevant services.
- **Activity log:** written on expense create/edit/delete, member join/leave, settlement — ready for an
  activity feed endpoint (not yet exposed via controller).
- **Security:** Spring Security + stateless JWT filter, BCrypt, global CORS, global exception handler.
- Docker Compose (Postgres + backend), multi-stage Dockerfile, Swagger/OpenAPI config.
- **Unit tests** for the two highest-risk pieces: split calculation and debt simplification.

## 🔜 Not yet built (next iterations)

- Activity feed endpoint (data is being logged, just needs a controller + DTO)
- Receipt upload to S3
- CSV import (Splitwise migration) + CSV/PDF export
- Search & filters across expenses (beyond friend search, which exists)
- Google Sign-In verification (`GoogleIdTokenVerifier`)
- More test coverage (expense/balance service integration tests with Testcontainers)

Next logical step: **CSV import** (the migration feature is the whole point of Phase 1) — that needs the
expense/split engine to exist first, which it now does, so it's unblocked.

## Running locally

```bash
# 1. Start Postgres + backend together
docker compose up --build

# OR, for local dev with hot-reload against a local Postgres only:
docker compose up postgres -d
mvn spring-boot:run
```

Backend will be at `http://localhost:8080`, Swagger UI at `http://localhost:8080/swagger-ui.html`.

### Environment variables (production)

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD` | Postgres connection |
| `JWT_SECRET` | **Must** be changed from the dev default, 32+ chars |
| `JWT_EXPIRATION_MS` | Access token lifetime (default 15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | Refresh token lifetime (default 7 days) |
| `AWS_S3_BUCKET`, `AWS_REGION` | For receipt uploads (Phase 1 later step) |

## Try it now

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name":"Alex","email":"alex@example.com","password":"password123"}'
```

Returns `accessToken` + `refreshToken`. Use the access token as `Authorization: Bearer <token>` on protected
routes (everything under `/api/v1/**` except `/auth/**`).

## Project structure

```
src/main/java/com/splitwise/app/
  config/       Security + OpenAPI config
  security/     JWT service, filter, UserDetailsService
  entity/       JPA entities (all Phase-1 tables)
  repository/   Spring Data repositories
  service/      Business logic (AuthService done, more coming)
  controller/   REST controllers (AuthController done, more coming)
  dto/          Request/response DTOs, organized by feature
  exception/    ApiException + global handler
  util/         SecurityUtils (get current user id from JWT)
src/main/resources/db/migration/   Flyway SQL migrations
```
