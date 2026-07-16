# Production Readiness TODO

Compiled 2026-07-15 from a full audit of the codebase (security, data layer, test coverage, ops/deployment). Reflects state after the V8/V9 schema-alignment migrations landed on `main`. Updated 2026-07-15 and 2026-07-16 as items below were resolved — see `DEPLOYMENT.md` for the operational detail behind the deployment/secrets/observability items.

**Overall assessment:** all seven items originally listed as blockers are now resolved. The remaining open items are lower-severity cleanup, not launch blockers.

---

## Before real users touch this (true blockers)

1. ~~**Swagger/OpenAPI is publicly exposed in prod**~~ — ✅ **Fixed.** Gated behind the `prod` profile check in `SecurityConfig`, and `springdoc.api-docs.enabled=false` in `application-prod.yml` as defense in depth. Verified against a real boot: 200 without the profile, 403 with it.
2. ~~**No password-reset / forgot-password flow.**~~ — ✅ **Fixed.** `POST /api/v1/auth/forgot-password` and `/reset-password`, backed by a `password_reset_tokens` table (V10), SMTP email delivery, rate-limited. Verified end-to-end: register → request reset → real token from the DB → reset → old password rejected, new one works, token can't be replayed.
3. ~~**No deployment pipeline.**~~ — ✅ **Fixed.** CI now has a `deploy` job that ships to Railway on green `main` builds. See `DEPLOYMENT.md` for the one-time setup (still required — the pipeline is code-complete but inert until `RAILWAY_TOKEN`/`RAILWAY_SERVICE_NAME` are added).
4. ~~**No migration rollback plan.**~~ — ✅ **Documented.** `DEPLOYMENT.md` has a full runbook (fix-forward pattern, pre-migration backup discipline, recovery steps). Flyway community still has no native undo — this is process, not tooling.
5. ~~**Local file storage has no production guard rail.**~~ — ✅ **Fixed.** `StorageProperties` fails fast at startup if `app.storage.provider=local` while the `prod` profile is active. Verified both directions against a real boot.
6. ~~**No test would catch a repeat of the V8/V9 bug.**~~ — ✅ **Fixed.** `SchemaValidationTest` boots the full app against a genuinely fresh Testcontainers Postgres with `ddl-auto=validate`. Verified it actually catches drift (it caught two more gaps — `lesson_note_attachments`/`lesson_question_status_history` missing `updated_at` — while being added).
7. ~~**Confirm whether vehicle/instructor-profile/student-profile management is actually missing, or just handled elsewhere.**~~ — ✅ **Resolved.** Decision: it was a real gap, not handled elsewhere — built all three. `VehicleController`/`VehicleService` (admin create/update/status-change, authenticated read), `InstructorProfileController`/`InstructorProfileService` (self-service `/me`, admin list-by-school and activate/deactivate), `StudentProfileController`/`StudentProfileService` (self-service `/me`, admin list-by-school and status-update) — all following the existing `SchoolController` pattern. Verified against a real Postgres boot for each.

---

## Security

| Status | Item | Notes |
|---|---|---|
| ✅ Fixed | Swagger exposed in prod | See blocker #1 above |
| ✅ Fixed | No refresh-token revocation | Decision: Redis-backed denylist keyed by each token's `jti` claim, TTL'd to the token's own remaining lifetime (no cleanup job needed). New `POST /api/v1/auth/logout` revokes the presented refresh token; `/refresh-token` now checks revocation first. Verified against a real Postgres+Redis boot: refresh works, logout revokes it, the same token is then rejected. |
| ✅ Fixed | No password-reset flow | See blocker #2 above |
| ⏳ Open | Rate limiting is narrow | `RateLimitingFilter` covers the 5 auth endpoints (login/register/refresh/forgot-password/reset-password) via one shared bucket — not per-IP/per-user. Nothing else in the API is throttled. |
| ✅ Fixed | Security tests only existed for Booking | 56 new tests across the other 9 controllers prove every `@PreAuthorize` role check is enforced at the HTTP layer, not just correct in isolation. |

**Already solid, no action needed:** JWT secret enforces a ≥32-char minimum; BCrypt strength 12; CORS fully externalized with a real prod default (no wildcard); prod disables stack traces and binding-error leakage; bootstrap-admin creation disabled by default in prod; `@PreAuthorize` with ownership checks consistently present; request DTOs use `@Valid`; dependency versions current.

---

## Data layer & schema

| Status | Item | Notes |
|---|---|---|
| ✅ Fixed | Cascade-delete chains, no soft-delete | Decision: soft-delete over hard cascade-delete. Added `users.deleted_at` (V12), `User.softDelete()`/`isDeleted()` (reuses the existing `enabled=false` gate on login — no auth-flow changes needed), admin `DELETE /api/v1/users/{id}`, and self-service `DELETE /api/v1/auth/me`. Verified against a real boot: login is rejected afterward while the user row and linked profile rows survive intact; re-deleting an already-deleted account returns 400. |
| ⚠️ Partially addressed | V9's `ADD COLUMN IF NOT EXISTS ... NOT NULL` can silently skip tightening | V11 directly tightened the columns originally flagged in this session's audit (`video_lessons`, `quizzes`, `quiz_questions`, `quiz_submissions`, `attendances`, `lesson_question_submissions`, `notifications`). The columns V9 itself added with this pattern (`vehicles.color`/`status`, `student_profiles.status`, `notifications.channel`/`status`, `license_workflows.*`) weren't re-touched — low risk in practice since Hibernate's `ddl-auto=update` would have created them correctly on any DB that hit this path, but not verified. |
| ✅ Fixed | Two entity-declared indexes were never created | `idx_video_lessons_order` and `idx_schools_name`/`idx_schools_active` added in V11. |
| ✅ Fixed | Nullable-in-DB vs. `nullable=false`-in-entity mismatches | Tightened in V11 for all columns identified in the original audit. |
| ⏳ Open | Sparse `@EntityGraph`/`JOIN FETCH` usage | Only 2 of many repositories use fetch joins. No confirmed N+1 in a static pass — worth checking with SQL logging on list endpoints if this becomes a real perf concern. |
| ⏳ Open | Varchar length mismatches | Cosmetic, DB column is always the wider one — harmless. |
| ⏳ Open | Timestamp/timezone discipline | Valid UTC pattern in place; worth a spot-check for any `LocalDateTime.now()` call under a non-UTC server default, but no known issue. |

**Already solid:** transaction boundaries are consistent across the service layer.

---

## Test coverage & quality

| Status | Item | Notes |
|---|---|---|
| ✅ Fixed | No integration/DB tests existed | `SchemaValidationTest` — see blocker #6. |
| ✅ Fixed | No migration/boot-validation test | Same as above. |
| ✅ Fixed | No service/controller layer for vehicles, instructor profiles, or student profiles | See blocker #7 above. Each module has both service-layer unit tests and `@WebMvcTest` security tests covering every `@PreAuthorize` check. |
| ✅ Fixed | Security testing was inconsistent | See Security table above. |
| ✅ Fixed | `LessonQuestionSubmissionService` has zero unit tests | 19 new tests cover submit/respond/updateStatus/getQuestion access checks and all four listing methods' user-id-to-profile-id resolution. |
| ⏳ Open | No static analysis or coverage tooling | No Jacoco, Checkstyle, PMD, or SpotBugs configured. |

**For contrast, not a gap:** `QuizServiceImplTest` and `BookingServiceImplTest` remain the bar the untested modules should be held to.

---

## Ops & deployment

| Status | Item | Notes |
|---|---|---|
| ✅ Fixed | No deployment pipeline | See blocker #3. |
| ✅ Documented | No migration rollback strategy | See blocker #4. |
| ✅ Fixed | Local storage had no production guard rail | See blocker #5. |
| ✅ Fixed | No metrics/APM | `/actuator/prometheus` (Micrometer), still behind the existing ADMIN-only actuator rule. Along the way, found and fixed a pre-existing bug where `exclude: "*"` in `application-prod.yml` was silently blocking every actuator endpoint except health regardless of `include` — `info` was already broken before this, not just the new endpoint. |
| ✅ Fixed | No error-tracking integration | Sentry wired in (`sentry-spring-boot-starter-jakarta`), inert until `SENTRY_DSN` is set. |
| ✅ Addressed | No secrets manager | Deliberate decision, not a code gap: Railway's encrypted per-service env vars are the secrets strategy for this deployment (documented in `DEPLOYMENT.md`). A dedicated secrets manager (Vault, cloud provider) would be premature complexity without the infrastructure to back it — revisit if/when multi-environment or dynamic-credential needs arise. |
| ✅ Fixed | Logs were plain-text, not structured | Prod logs are now structured JSON (ECS format) via Spring Boot's built-in structured logging. Dev/local logs unchanged (still human-readable). Also found and fixed: a pre-existing YAML indentation bug in the base `application.yml` meant `management.endpoints.web.exposure.include` never actually bound in dev/test; and the auto-registered mail health indicator was failing liveness/readiness whenever SMTP isn't configured, even though `EmailService` already handles send failures gracefully — disabled via `management.health.mail.enabled=false`. |
| ⏳ Open | `docker-compose.yml` is dev-only | Fine for local dev; no equivalent prod-shaped manifest exists, but Railway (the actual deploy target) doesn't need one. |
| ⏳ Open | No backup strategy documented for the Postgres data volume beyond "Railway supports it" | Mentioned in the migration-rollback runbook; not deeply addressed (e.g. no automated backup schedule configured). |
| ✅ Fixed | Stale comment in `Dockerfile` | Removed as part of fixing the Dockerfile's test-execution bug (Testcontainers-based tests can't run inside a `docker build` layer). |

**Already solid:** Dockerfile is well-built (multi-stage, non-root user, container-aware JVM memory sizing, correct health-check); `application-prod.yml` already correctly disables bootstrap-admin creation by default.

---

## What's actually left

Everything above marked ⏳ Open is lower-severity cleanup (rate-limit breadth, N+1 risk, static analysis tooling, the not-fully-reverified V9 `ADD COLUMN IF NOT EXISTS` columns, docker-compose being dev-only, Postgres backup automation) — reasonable to pick up incrementally, none of it blocks going live. Both former blockers (#7 profile-management scope, cascade-delete policy) are resolved.
