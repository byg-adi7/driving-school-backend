# Production Readiness TODO

Compiled 2026-07-15 from a full audit of the codebase (security, data layer, test coverage, ops/deployment). Reflects state after the V8/V9 schema-alignment migrations landed on `main`. Updated 2026-07-15 and 2026-07-16 as items below were resolved — see `DEPLOYMENT.md` for the operational detail behind the deployment/secrets/observability items.

**Overall assessment:** all seven original blockers plus #8, #9, and #10 (all discovered and fixed on 2026-07-16, each one found while verifying the previous fix) are resolved. Everything else open is lower-severity cleanup, not a launch blocker.

---

## Before real users touch this (true blockers)

1. ~~**Swagger/OpenAPI is publicly exposed in prod**~~ — ✅ **Fixed.** Gated behind the `prod` profile check in `SecurityConfig`, and `springdoc.api-docs.enabled=false` in `application-prod.yml` as defense in depth. Verified against a real boot: 200 without the profile, 403 with it.
2. ~~**No password-reset / forgot-password flow.**~~ — ✅ **Fixed.** `POST /api/v1/auth/forgot-password` and `/reset-password`, backed by a `password_reset_tokens` table (V10), SMTP email delivery, rate-limited. Verified end-to-end: register → request reset → real token from the DB → reset → old password rejected, new one works, token can't be replayed.
3. ~~**No deployment pipeline.**~~ — ✅ **Fixed.** CI now has a `deploy` job that ships to Railway on green `main` builds. See `DEPLOYMENT.md` for the one-time setup (still required — the pipeline is code-complete but inert until `RAILWAY_TOKEN`/`RAILWAY_SERVICE_NAME` are added).
4. ~~**No migration rollback plan.**~~ — ✅ **Documented.** `DEPLOYMENT.md` has a full runbook (fix-forward pattern, pre-migration backup discipline, recovery steps). Flyway community still has no native undo — this is process, not tooling.
5. ~~**Local file storage has no production guard rail.**~~ — ✅ **Fixed.** `StorageProperties` fails fast at startup if `app.storage.provider=local` while the `prod` profile is active. Verified both directions against a real boot.
6. ~~**No test would catch a repeat of the V8/V9 bug.**~~ — ✅ **Fixed.** `SchemaValidationTest` boots the full app against a genuinely fresh Testcontainers Postgres with `ddl-auto=validate`. Verified it actually catches drift (it caught two more gaps — `lesson_note_attachments`/`lesson_question_status_history` missing `updated_at` — while being added).
7. ~~**Confirm whether vehicle/instructor-profile/student-profile management is actually missing, or just handled elsewhere.**~~ — ✅ **Resolved.** Decision: it was a real gap, not handled elsewhere — built all three. `VehicleController`/`VehicleService` (admin create/update/status-change, authenticated read), `InstructorProfileController`/`InstructorProfileService` (self-service `/me`, admin list-by-school and activate/deactivate), `StudentProfileController`/`StudentProfileService` (self-service `/me`, admin list-by-school and status-update) — all following the existing `SchoolController` pattern. Verified against a real Postgres boot for each.
8. ~~**`POST /api/v1/lesson-notes` (creating a lesson note) is completely broken.**~~ — ✅ **Fixed.** Root cause: `LessonNote` redeclared its own `createdAt`/`updatedAt` fields using Hibernate's `@CreationTimestamp`/`@UpdateTimestamp`, which shadowed the working `@CreatedDate`/`@LastModifiedDate` fields it already inherited from `BaseEntity` (every other entity in the codebase just inherits these - `LessonQuestionSubmission` even has a comment saying so). The shadowed field never got populated, so `created_at` stayed null and the insert failed its `NOT NULL` constraint. The identical anti-pattern also existed in `LessonNoteAttachment` (same fix applied there, proactively, before it caused the same failure on an actual upload attempt). Verified against a real boot: `POST /api/v1/lesson-notes` now succeeds with a real `createdAt` timestamp.
9. ~~**Local file storage uploads may be silently broken in this container.**~~ — ✅ **Fixed.** `LocalStorageService` passed a relative `Path` to `MultipartFile.transferTo(File)`, which Spring/Tomcat resolves against Tomcat's own internal temp/work directory rather than the JVM's working directory - confirmed by the original error, which pointed at `/tmp/tomcat.<port>.<id>/work/Tomcat/localhost/ROOT/./uploads/...` instead of `/app/uploads`. Fixed by resolving `app.storage.local.base-path` to an absolute, normalized path once (`basePath()`), used consistently across `store`/`load`/`delete`. Verified against a real boot with a genuine multipart upload through actual Tomcat (not `MockMultipartFile`, which never exercised this code path): the file landed at the correct `/app/uploads/...` location, and a subsequent download round-tripped correctly.
10. ~~**`StackOverflowError` on any code path that stringifies a `LessonNote` or `LessonNoteAttachment` with both relationship directions loaded.**~~ — ✅ **Fixed, discovered while verifying #9.** `LessonNote` and `LessonNoteAttachment` both use Lombok's `@Data`, and their bidirectional relationship (`LessonNote.attachments` ↔ `LessonNoteAttachment.lessonNote`) meant each side's generated `toString()` recursed into the other infinitely. It surfaced as a transaction-commit failure during a download (a `@PreUpdate` auditing callback triggered a `toString()` somewhere in the flush path), but the same crash is reachable from any logging statement or exception message that stringifies either entity. A full codebase sweep confirmed this is the *only* bidirectional `@Data` pair in the entity model - nothing else needs the same fix. Fixed with `@ToString.Exclude` on both sides of the relationship, verified with a direct unit test and against a real boot (upload → download → list all succeeded without a crash).

---

## Security

| Status | Item | Notes |
|---|---|---|
| ✅ Fixed | Swagger exposed in prod | See blocker #1 above |
| ✅ Fixed | No refresh-token revocation | Decision: Redis-backed denylist keyed by each token's `jti` claim, TTL'd to the token's own remaining lifetime (no cleanup job needed). New `POST /api/v1/auth/logout` revokes the presented refresh token; `/refresh-token` now checks revocation first. Verified against a real Postgres+Redis boot: refresh works, logout revokes it, the same token is then rejected. |
| ✅ Fixed | No password-reset flow | See blocker #2 above |
| ✅ Fixed | Rate limiting is narrow | Was worse than "narrow": a single shared in-memory bucket meant every client competed for the same 10 req/min, so one abusive caller could lock out everyone else from login. Replaced with a Redis-backed fixed-window counter keyed per-client-IP - auth endpoints keep a strict 10 req/min, the rest of the API gets a looser 100 req/min (previously completely unthrottled), health-check paths are exempt so Docker/Railway probes never 429. Verified against a real boot: 10 requests decrement `X-Rate-Limit-Remaining` from 9 to 0, the 11th returns 429 with a `Retry-After`, the general-API bucket is confirmed separate (still at 99/100), and health checks pass through unaffected even with the auth bucket exhausted. |
| ✅ Fixed | Security tests only existed for Booking | 56 new tests across the other 9 controllers prove every `@PreAuthorize` role check is enforced at the HTTP layer, not just correct in isolation. |

**Already solid, no action needed:** JWT secret enforces a ≥32-char minimum; BCrypt strength 12; CORS fully externalized with a real prod default (no wildcard); prod disables stack traces and binding-error leakage; bootstrap-admin creation disabled by default in prod; `@PreAuthorize` with ownership checks consistently present; request DTOs use `@Valid`; dependency versions current.

---

## Data layer & schema

| Status | Item | Notes |
|---|---|---|
| ✅ Fixed | Cascade-delete chains, no soft-delete | Decision: soft-delete over hard cascade-delete. Added `users.deleted_at` (V12), `User.softDelete()`/`isDeleted()` (reuses the existing `enabled=false` gate on login — no auth-flow changes needed), admin `DELETE /api/v1/users/{id}`, and self-service `DELETE /api/v1/auth/me`. Verified against a real boot: login is rejected afterward while the user row and linked profile rows survive intact; re-deleting an already-deleted account returns 400. |
| ✅ Verified | V9's `ADD COLUMN IF NOT EXISTS ... NOT NULL` can silently skip tightening | V11 directly tightened the columns originally flagged in this session's audit. The columns V9 itself added with this pattern (`vehicles.color`/`status`, `student_profiles.status`, `notifications.channel`/`status`, `license_workflows.current_stage`/`theory_progress_percent`/`road_training_hours`/`stage_updated_at`) were checked directly against `information_schema.columns` on the dev database (which has run the full V1-V12 migration chain and boots with `ddl-auto=validate`, so nothing could have silently patched them): all 9 are genuinely `NOT NULL`. No drift occurred. |
| ✅ Fixed | Two entity-declared indexes were never created | `idx_video_lessons_order` and `idx_schools_name`/`idx_schools_active` added in V11. |
| ✅ Fixed | Nullable-in-DB vs. `nullable=false`-in-entity mismatches | Tightened in V11 for all columns identified in the original audit. |
| ✅ Fixed | Sparse `@EntityGraph`/`JOIN FETCH` usage | A full audit found 5 real, unmitigated N+1s: live sessions dashboard (also had a per-row attendance-count query, now batched into one grouped query), instructor/student-by-school listings, instructor question inbox, booking lists, and lesson notes. All fixed with `JOIN FETCH`/`LEFT JOIN FETCH` on the repository queries actually used by each endpoint - two of the audit's original method names turned out to be dead code, confirmed by grep before fixing so effort landed on the real call sites. Along the way, found that `LessonQuestionSubmissionService`/`LessonNoteService`'s mappers were routing student/instructor names through `getUser().getDisplayName()`, which triggers a *third* lazy hop (`User.studentProfile`/`User.instructorProfile`) beyond what the fetch join covers - simplified to read the name directly off the already-loaded profile instead. Verified against a real boot: all 5 areas' endpoints return correct data with the new queries. |
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
| ✅ Fixed | No coverage tooling | Jacoco wired into `mvn test` (report only, no enforced threshold - a coverage floor is a policy call, not made here). CI uploads the HTML report as a build artifact on every run. Baseline: 53% instruction coverage. |
| ✅ Fixed | No static analysis tooling | Added SpotBugs (report only, never fails the build) rather than a style linter (Checkstyle/PMD) - it flags real bug patterns instead of formatting preferences, so it didn't need a rule-set decision. CI uploads the XML report as a build artifact. The full baseline (121 findings, not the 39 originally estimated - an earlier parsing mistake undercounted it) has been fully triaged: 3 `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` were real NPEs and got fixed (`FileValidator.getFileExtension()` null-checks its argument now; `LocalStorageService.store()` guards a `Path.getParent()` that could theoretically be null); 1 `CT_CONSTRUCTOR_THROW` fixed by making `JwtTokenProvider` `final` (closes the finalizer-attack vector); 120 `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` reviewed field-by-field across 66 classes - all were JPA entity-to-entity references, Spring DI constructor parameters, or plain response DTOs (no secrets/tokens/credentials among them) except `UserPrincipal.authorities`, which is genuinely security-relevant (read by Spring Security's authorization checks) and was hardened with `Set.copyOf(...)`. The rest are excluded via `spotbugs-exclude.xml` with the reasoning documented inline. **Current baseline: 0 findings.** |

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
| ✅ Documented | No backup strategy documented for the Postgres data volume beyond "Railway supports it" | `DEPLOYMENT.md` now has a concrete "Backups" section: turn on scheduled (daily minimum) backups in the Railway dashboard, always take a manual on-demand backup before a risky migration, the actual restore steps (including updating `DB_HOST`/etc. if Railway assigns a new instance), and a reminder to test a restore before you actually need one. Turning on the schedule itself is a one-time dashboard action, not something expressible in this repo's config. |
| ✅ Fixed | Stale comment in `Dockerfile` | Removed as part of fixing the Dockerfile's test-execution bug (Testcontainers-based tests can't run inside a `docker build` layer). |

**Already solid:** Dockerfile is well-built (multi-stage, non-root user, container-aware JVM memory sizing, correct health-check); `application-prod.yml` already correctly disables bootstrap-admin creation by default.

---

## What's actually left

Everything marked ⏳ Open is lower-severity cleanup (a style linter like Checkstyle/PMD if wanted - that still needs a rule-set decision, docker-compose being dev-only, varchar/timestamp cosmetics) — reasonable to pick up incrementally, none of it blocks going live. All blockers requiring code (#7 profile-management scope, cascade-delete policy, #8 lesson note creation, #9 file upload path resolution, #10 toString StackOverflowError) are resolved.
