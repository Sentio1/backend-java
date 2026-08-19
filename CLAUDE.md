# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Java backend for **Sentio**, a legal-practice management SaaS (Ukrainian legal market — case/deadline tracking,
clients, court registry monitoring, document templates). This repo is a Gradle multi-module monorepo containing the
Java side only; other services (`Notifications`, `Registry Monitor`, `document-service`, `case-service`) are separate
Go/other-language repos that consume JWTs this repo issues but never validate on their own.

Modules:
- **`user-service`** — the only module with real code today. Owns auth, users, and organizations. Spring Boot 4 app.
- **`shared-core`** — placeholder module (`java-library`), currently empty (no source files). Intended for code
  shared across future Java services.

The full intended product schema lives in `user-service/database-structure.txt` (dbdiagram.io DBML format, in
Ukrainian) — cases, deadlines, clients, courts, documents, notifications, audit log. Only the auth/org subset of that
schema (`users`, `organizations`, `user_identities`, `organization_members`, `refresh_tokens`) is actually migrated
in `src/main/resources/db/migration/` so far; the rest is a forward-looking design doc, not yet implemented.

## Custom starters (critical context)

Most of the auth/security/JPA machinery is **not in this repo**. It comes from private published Spring Boot
starters under the `com.lisovskyi` group (see `gradle/libs.versions.toml`):

- `lisovskyi-security-starter` — JWT (`JwtService`, RSA key handling, JWKS), `JwtBlacklistService`,
  `OpaqueTokenService` (refresh-token hashing), `SecurityPrincipal`, `AuthCookieService`, `SecurityFilterChainCustomizer`
  (extension point apps use to append to the security filter chain, e.g. `OAuth2SecurityConfig`).
- `lisovskyi-jpa-starter` — `TimestampedEntity`/`CreationTimestampedEntity` base classes, sequence-based ID
  generation (`SequenceSize`).
- `lisovskyi-web-error-starter` — standard exceptions (`ResourceNotFoundException`, `ResourceAlreadyExistsException`,
  `UnauthorizedException`), `ErrorResponse`, `@PasswordsMatch` validation.

When something looks "missing" (a JWT util, an exception type, a base entity), check these starters before assuming
it needs to be written — grep their sources under `~/.gradle`/`~/.m2` caches or the GitHub Packages registry
(`maven.pkg.github.com/Sentio1/backend-java`) configured as a repository in the root `build.gradle.kts`.

## Build & run

Requires Java 25 (toolchain-pinned in root `build.gradle.kts`). All commands from repo root using the wrapper.

```bash
./gradlew build                          # build all modules
./gradlew :user-service:bootRun          # run user-service (needs Postgres/env, see below)
./gradlew :user-service:dopplerRun       # run via Doppler-injected secrets (custom task)
./gradlew :user-service:test             # run all tests (Testcontainers spins up Postgres)
./gradlew :user-service:test --tests "com.sentio.user_service.auth.AuthServiceTest"
./gradlew :user-service:test --tests "com.sentio.user_service.auth.AuthServiceTest.methodName"
```

- Tests use Testcontainers (`TestcontainersConfiguration`) — a real `postgres:17-alpine` container per test run, no
  local Postgres required for `test`. Docker must be available.
- Local dev (`bootRun`, profile `dev`) needs a running Postgres (and Redis for rate limiting) — `docker-compose.yaml`
  in `user-service/` provides both. Config is pulled from `.env`/`.env.properties` at repo root or `user-service/`
  (gitignored), referenced via `spring.config.import` in `application-dev.yaml`/`application-prod.yaml`.
- Spotless (`palantirJavaFormat`) is wired into the root build but currently **commented out** in `build.gradle.kts`
  — don't assume formatting is auto-enforced.
- GitHub Packages credentials (`gpr.user`/`gpr.token` gradle properties or `GITHUB_ACTOR`/`GITHUB_TOKEN` env vars)
  are needed to resolve the `com.lisovskyi.*` starters.

## Architecture (`user-service`)

Package root: `com.sentio.user_service`. Organized by feature, not by layer:

- **`auth/`** — `AuthController` → `AuthService` (orchestrator only; delegates real work) → collaborators:
  - `token/TokenIssuer` — mints JWT access token + opaque refresh token pair.
  - `organization/OrganizationProvisioningService` — creates a new org (registering as `OWNER`) or joins an existing
    one by slug.
  - `oauth/GoogleAccountResolver` — resolves/creates a `User` from a Google identity, linking `UserIdentity` rows.
  - `rate_limiting/RateLimitingService` — wraps Resilience4j rate limiters (`login-by-email`, `login-by-ip`,
    `register-by-email`, `register-by-ip`, configured in `application.yaml`); `RateLimitExceptionHandler` maps
    `RequestNotPermitted` to HTTP responses.
  - `oauth/GoogleOAuth2SuccessHandler`/`FailureHandler` — plugged into Spring Security's OAuth2 login flow via
    `security/OAuth2SecurityConfig`, which implements `SecurityFilterChainCustomizer` from the security starter
    rather than defining its own `SecurityFilterChain` from scratch.
  - Access/refresh tokens are set as cookies (`AuthCookieService` from the security starter), not returned in JSON
    bodies — see `AuthController`.
- **`organization/`** — `Organization`/`OrganizationMember` entities, `OrganizationService`/`OrganizationSecurity`
  (authorization checks, e.g. `@PreAuthorize`-backed), `OrganizationController` (management API).
- **`user/`** — `User`/`UserIdentity` entities, repositories, `UserController`/`UserService`.
- **`refresh_token/`** — `RefreshToken` entity + repository; tokens are stored **hashed** (`OpaqueTokenService`),
  never in plaintext.
- Each feature package has a `*Constants` class (`AuthConstants`, `OrganizationConstants`, `UserConstants`) for
  path/claim/limit literals rather than scattering magic strings.
- MapStruct (`*Mapper` classes, e.g. `AuthMapper`, `OrganizationMapper`, `UserMapper`) handles entity↔DTO mapping.
  Lombok + MapStruct interaction requires `lombok-mapstruct-binding` annotation processor ordering — see the comment
  in `user-service/build.gradle.kts` if mapped fields come back null.

### Multi-tenancy model

A user can belong to multiple organizations via `organization_members` (role: `OWNER`/`LAWYER`/`ASSISTANT`), but
exactly one membership is flagged `is_default = true` (enforced by a partial unique index, migration `V4`). The JWT
is scoped to that **one** default org (`org_id` claim) — there's no cross-org token. Platform-level role
(`ADMIN`/`USER`) is separate from org role and lives on `User.platformRole`.

### Auth/JWT contract (see `README.md` for full detail, in Ukrainian)

This is a cross-repo contract with the Go services — don't change claim shapes without updating `README.md` and
coordinating (SEN-33 in Linear). Key points:
- RS256, `kid`-tagged, private key never leaves `user-service`.
- JWKS at `GET /api/v1/.well-known/jwks.json` (public) — note the path is under `server.servlet.context-path`
  (`/api/v1`), not domain-root `/.well-known/...` as convention would suggest.
- Claims: `sub`/`id` (user id, string+number dupes), `org_id` (default org), `roles` (org role(s) + `"ADMIN"` if
  platform admin; `"USER"` is never added), `iss`, `iat`, `exp`. Access tokens live 15 min.
- Key rotation keeps both current and previous key in JWKS simultaneously (`app.jwt.previous-private-key`) so
  in-flight tokens don't fail validation mid-rotation.
- Service-to-service calls (Go → Java) with a `SERVICE` role token are planned but **not implemented yet**.

## Testing conventions

- `*Test` = unit tests (Mockito-style, no Spring context) — e.g. `AuthServiceTest`, `TokenIssuerTest`.
- `*IT` = integration tests (`@SpringBootTest` + Testcontainers + MockMvc) — e.g. `AuthControllerIT`,
  `JwksControllerIT`. Bootstrapped via `TestUserServiceApplication`/`TestcontainersConfiguration`.
- `src/test/resources/application.yaml` overrides JWT keys (a fixed test-only RSA key, never the real one) and OAuth2
  client registration with dummy values — real secrets are never needed to run tests.
