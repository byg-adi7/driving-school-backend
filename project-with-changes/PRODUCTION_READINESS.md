# Production Readiness TODO

Compiled 2026-07-15 from a full audit of the codebase (security, data layer, test coverage, ops/deployment). Reflects state after the V8/V9 schema-alignment migrations landed on `main`. Updated 2026-07-15 and 2026-07-16 as items below were resolved — see `DEPLOYMENT.md` for the operational detail behind the deployment/secrets/observability items. A separate business-workflow correction (practical lesson booking) landed 2026-07-16 — see the dedicated section near the end of this file.

**Overall assessment:** all seven original blockers plus #8, #9, and #10 (all discovered and fixed on 2026-07-16, each one found while verifying the previous fix) are resolved. Everything else open is lower-severity cleanup, not a launch blocker.

---

## Before real users touch this (true blockers)

1. ~~**Swagger/OpenAPI is publicly exposed in prod**~~ — ✅ **Fixed.** Gated behind the `prod` profile check in `SecurityConfig`, and `springdoc.api-docs.enabled=false` in `application-prod.yml` as defense in depth. Verified against a real boot: 200 without the profile, 403 with it.
2. ~~**No password-reset / forgot-password flow.**~~ — ✅ **Fixed.** `POST /api/v1/auth/forgot-password` and `/reset-password`, backed by a `password_reset_tokens` table (V10), SMTP email delivery, rate-limited. Verified end-to-end: register → request reset → real token from the DB → reset → old password rejected, new one works, token can't be replayed.
3. ~~**No deployment pipeline.**~~ — ✅ **Fixed.** CI now has a `deploy` job that ships to Railway on green `main` builds. See `DEPLOYMENT.md` for the one-time setup (still required — the pipeline is code-complete but inert until `RAILWAY_TOKEN`/`RAILWAY_SERVICE_NAME` are added). *(2026-09-29: hosting moved to Render, which deploys `main` itself after CI passes - the Railway `deploy` job is gone; see "Hosting Moved to Render" below.)*
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
| ✅ Fixed | Upload validation trusted the client-supplied filename/Content-Type, never the actual bytes | `FileValidator` now checks the real content: rejects anything whose bytes don't start with the PDF signature (`%PDF-`) or lack a trailing `%%EOF`, and rejects PDFs containing active-content dictionary keys (`/JavaScript`, `/JS`, `/Launch`, `/EmbeddedFile`, `/OpenAction`, `/AA`) that have no legitimate use in a static lesson-note attachment. This is byte-pattern structural validation, not true antivirus scanning — no ClamAV/AV engine is wired in (a deliberate scope decision: that needs a running scanning daemon as new infrastructure, not just a code change). |

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
| ✅ Addressed | No secrets manager | Deliberate decision, not a code gap: Render's per-service environment variables (Railway's before 2026-09-29) are the secrets strategy for this deployment (documented in `DEPLOYMENT.md`). A dedicated secrets manager (Vault, cloud provider) would be premature complexity without the infrastructure to back it — revisit if/when multi-environment or dynamic-credential needs arise. |
| ✅ Fixed | Logs were plain-text, not structured | Prod logs are now structured JSON (ECS format) via Spring Boot's built-in structured logging. Dev/local logs unchanged (still human-readable). Also found and fixed: a pre-existing YAML indentation bug in the base `application.yml` meant `management.endpoints.web.exposure.include` never actually bound in dev/test; and the auto-registered mail health indicator was failing liveness/readiness whenever SMTP isn't configured, even though `EmailService` already handles send failures gracefully — disabled via `management.health.mail.enabled=false`. |
| ⏳ Open | `docker-compose.yml` is dev-only | Fine for local dev; no equivalent prod-shaped manifest exists, but Render (the actual deploy target) builds straight from the Dockerfile and doesn't need one. |
| ✅ Documented | No backup strategy documented for the Postgres data volume beyond "Railway supports it" | `DEPLOYMENT.md` now has a concrete "Backups" section: turn on scheduled (daily minimum) backups in the Railway dashboard, always take a manual on-demand backup before a risky migration, the actual restore steps (including updating `DB_HOST`/etc. if Railway assigns a new instance), and a reminder to test a restore before you actually need one. Turning on the schedule itself is a one-time dashboard action, not something expressible in this repo's config. *(2026-09-29: rewritten for Render Postgres - point-in-time recovery plus on-demand exports; see `DEPLOYMENT.md`.)* |
| ✅ Fixed | Stale comment in `Dockerfile` | Removed as part of fixing the Dockerfile's test-execution bug (Testcontainers-based tests can't run inside a `docker build` layer). |

**Already solid:** Dockerfile is well-built (multi-stage, non-root user, container-aware JVM memory sizing, correct health-check); `application-prod.yml` already correctly disables bootstrap-admin creation by default.

---

## What's actually left

Everything marked ⏳ Open is lower-severity cleanup (a style linter like Checkstyle/PMD if wanted - that still needs a rule-set decision, docker-compose being dev-only, varchar/timestamp cosmetics) — reasonable to pick up incrementally, none of it blocks going live. All blockers requiring code (#7 profile-management scope, cascade-delete policy, #8 lesson note creation, #9 file upload path resolution, #10 toString StackOverflowError) are resolved.

---

## Practical Lesson Booking: Workflow Correction (2026-07-16)

A separate engagement from the audit above: the booking module let **students** self-create practical lesson bookings, which contradicted the real business workflow (instructors schedule lessons for students they already teach; students are only notified, never initiate). This also surfaced several business-logic and conceptual-modeling bugs found while reviewing the area.

1. ~~**STUDENT role could self-create bookings.**~~ — ✅ **Fixed.** `POST /api/v1/bookings` now requires `hasRole('ADMIN')` or `(hasRole('INSTRUCTOR') and @bookingSecurity.isSelfInstructor(...))`. Students lose create access entirely.
2. ~~**No same-school check between instructor and student at booking creation.**~~ — ✅ **Fixed.** An instructor at School A could book a lesson for a student at School B. `BookingServiceImpl.create` now rejects cross-school pairings with a `BadRequestException`.
3. ~~**Instructor impersonation gap.**~~ — ✅ **Fixed.** Any INSTRUCTOR could set `instructorId` in the request body to *any* instructor's ID, not just their own — there was no self-check on create (unlike the student-side check that was just removed). Closed by the same `isSelfInstructor` change as #1.
4. ~~**No student-double-booking conflict check.**~~ — ✅ **Fixed.** Only instructor and vehicle conflicts were checked; a student could be booked into two overlapping lessons. Added `existsStudentConflict` (mirrors the existing instructor/vehicle conflict queries) and wired it into `validateNoConflicts`.
5. ~~**`pickupLocation` field on bookings.**~~ — ✅ **Removed.** Pickup is always the driving school (a frontend constant), not API state. Dropped from the entity, both DTOs, the mapper, and the `bookings` table (V13).
6. ~~**Notifications had no read/unread state and zero cross-module callers.**~~ — ✅ **Fixed.** Added `IN_APP` channel, a nullable `read_at` timestamp (consistent with how `sent_at` already works — no redundant boolean), `GET /api/v1/notifications/me` and `PATCH /api/v1/notifications/{id}/read`, and wired `BookingServiceImpl.create` to automatically send the student an `IN_APP` notification on lesson scheduling. The notification send is wrapped so a notification failure can never roll back or fail the booking itself (see the transaction-propagation gotcha below).
7. ~~**`PracticalLessonRoute` and `LessonNote` were wired to a raw, unvalidated `liveSessionId`.**~~ — ✅ **Fixed.** Both referenced a virtual `LiveSession` concept even though a route/note is actually about a physical `Booking`. `PracticalLessonRoute.liveSessionId` → a required `Booking` FK with an ownership check (an instructor can only generate a route for their own booking). `LessonNote.liveSessionId` → an *optional* `Booking` FK (notes stay keyed to the student/instructor pair they already have and remain accessible independent of any booking's existence, per the decision that notes shouldn't depend on a booking). `DrivingAssessment` got the same optional `Booking` FK added for future traceability (entity-only — no service/controller layer exists for it yet).
8. ~~**Transaction-propagation gotcha found during live verification.**~~ — ✅ **Fixed.** The original design assumed wrapping `notificationService.send(...)` in a try/catch inside `BookingServiceImpl.create()` would be enough to make notification failures non-fatal to the booking. It isn't: `NotificationServiceImpl.send()` is itself `@Transactional` (default `REQUIRED` propagation), so if it throws, Spring marks the *shared* transaction rollback-only before the exception ever reaches the catch block — the booking then fails at commit with `UnexpectedRollbackException` even though the Java-level exception was "caught". Root cause in this case was unrelated to propagation itself: a stale environment (see below) had a DB-level CHECK constraint that didn't yet allow the new `IN_APP` channel value, so every notification insert failed. Fixed at the source (see next item), which also resolves the propagation trap for this specific case — worth remembering generally, though, since any future notification-path exception would hit the same rollback-only issue.
9. ~~**A dev database had a Hibernate-autogenerated CHECK constraint never captured in any migration.**~~ — ✅ **Fixed.** `notifications_channel_check` only allowed `EMAIL`/`SMS`/`PUSH` — it was created by an earlier local `ddl-auto=update` pass, not by any Flyway migration, so it never showed up in a source-code search. Only surfaced once `IN_APP` was actually inserted against a real, previously-used dev database (a fresh/CI database built purely from migrations would never have had it). V13 now explicitly drops and recreates this constraint to include `IN_APP`, so it's correct regardless of whether a given environment already had the stale constraint.

**Verified against a real Postgres+Redis boot:** STUDENT gets 403 on create; INSTRUCTOR can't create under another instructor's ID; cross-school booking rejected; INSTRUCTOR creates a lesson for their own student successfully with no `pickupLocation` in the response; the student's `GET /notifications/me` shows the new `IN_APP` notification with correct lesson details and `readAt: null`; marking it read populates `readAt`; a second overlapping booking for the same instructor is rejected as a conflict; `LessonNote` creation with an optional `bookingId` correctly validates the booking belongs to the same instructor+student pair; `PracticalLessonRoute` generation correctly resolves and validates ownership of the referenced booking (verified up to the external OpenRouteService API boundary, which is unreachable in this environment — a pre-existing external dependency, not a regression).

**Environment note, not a code issue:** this machine has a native Windows PostgreSQL service also bound to port 5433, colliding with the docker-compose Postgres container's host port mapping — a native process (not the container) was silently answering connections intended for Docker. Worked around for this verification by remapping the container to a different host port; the underlying collision is a local machine configuration matter, not something this session changed or should change unilaterally.

---

## Learning/Course API (2026-07-17)

The `Course`/`VideoLesson`/`Resource` entities and their repositories already existed (and the schema was already correct - `SchemaValidationTest` was already passing against them), but there was no service, controller, DTO, or validator layer at all - the original external-review punch list's "missing learning API" item was a real gap, not something handled elsewhere.

Built following the same per-module pattern already established by the quiz module (`QuizValidator`/`QuizService`/`QuizController`):

- **`LearningValidator`** - ownership checks (`ADMIN` or the owning instructor may manage a course/lesson) and read-access checks (published courses/lessons are visible to any authenticated role; drafts are owner/ADMIN-only), mirroring `QuizValidator`'s exact policy.
- **`CourseController`/`CourseService`** - create (instructor creates under their own profile; `ADMIN` must target an explicit `instructorId`, mirroring the quiz submission module's self-vs-admin-supplied-ID pattern), update, publish/unpublish/archive, get-by-ID (read-access gated), list-published (any role), and `GET /mine` (`INSTRUCTOR`-only, all statuses, for managing their own catalog).
- **`VideoLessonController`/`VideoLessonService`** - create/update/publish/unpublish under a course (ownership-gated), get-by-ID, and list-by-course (returns every lesson to the owner/`ADMIN`, published-only to everyone else).
- **`ResourceController`/`ResourceService`** - create/delete under a video lesson (ownership-gated), list-by-lesson (gated by the lesson's own read-access rule).

51 new tests (service-layer unit tests + `@WebMvcTest` security-slice tests per controller, same split as every other module) cover ownership enforcement, the admin-vs-self instructor resolution on create, and the published/draft visibility filtering.

**Not verified against a real boot this time:** this machine's native-Postgres-vs-Docker port collision (documented above) reproduced again when attempting to boot the app for live verification. Given the thorough unit + security-slice coverage already in place, live verification was skipped rather than re-running the same port-remap workaround - worth doing before this ships if a clean environment is available.

---

## ID-Scheme Standardization (2026-07-17)

An external review flagged an "ID-scheme inconsistency": the same conceptual field (`studentId`/`instructorId`) meant `StudentProfile.id`/`InstructorProfile.id` in some modules (booking, quiz, progress, learning, live) but `User.id` in others (lesson-note, lesson-question, lesson-route). A full audit (grep + read every consuming service, not just the DTO names) confirmed this and found something worse than a naming nit: three services papered over the ambiguity with "try both" resolvers -

```java
studentProfileRepository.findByUserId(id).or(() -> studentProfileRepository.findById(id))
```

- which is a real correctness bug, not just confusing naming: for STUDENT/INSTRUCTOR callers a downstream ownership re-check happens to catch a wrong resolution (degrading to a confusing 400 rather than a leak), but for **ADMIN** queries - which skip ownership checks entirely, by design - an ID-space collision could silently return the wrong student's/instructor's notes, questions, or routes with no error at all.

**Fixed**, converging the three outlier modules onto the majority profile-id scheme (matching booking/quiz/progress/learning/live, and the `CurrentUserResponse`/`StudentProfileResponse`/`InstructorProfileResponse` precedent that already cleanly separates `userId` from `studentProfileId`/`instructorProfileId`):

- `lesson-note`: `CreateLessonNoteRequest.studentId`, `LessonNoteResponse.studentId`/`instructorId`, and the `/lesson-notes/student/{id}` / `/lesson-notes/instructor/{id}` path parameters are now `StudentProfile.id`/`InstructorProfile.id` throughout. The two dual-scheme resolver methods are gone - a caller must now pass the correct profile id, and an unresolvable one is a clean 404 instead of a silent misresolution.
- `lesson-question`: `SubmitQuestionRequest.assignedInstructorId` and `QuestionResponse.studentId`/`instructorId` are now profile ids. The three self-lookup methods (`getStudentQuestions`/`getInstructorQuestions`/`getInstructorPendingQuestions`, all reachable only via "my own questions"-style endpoints) no longer silently fall back to treating an unresolvable caller id as a raw profile id - they now throw `ResourceNotFoundException` if the caller has no matching profile.
- `lesson-route`: `RouteResponse.instructorId` and the `/lesson-routes/instructor/{id}` path parameter are now `InstructorProfile.id`; the dual-scheme resolver in `getInstructorRoutes` is gone.
- Purely internal "who is the caller" parameters (already correctly `User.id`, since that's what a JWT identifies) were renamed from `studentId`/`instructorId` to `callerId` in all three modules, so a future reader can't confuse them with the now-profile-id-scheme DTO fields living in the same files.

**Explicitly not a business-logic change:** every authorization rule (who can create/view/edit what) is byte-for-byte identical before and after this fix. Only the *meaning* of an ID value in a handful of request/response fields changed - a client sending the old (`User.id`) value to one of the three affected endpoints now needs to send the profile id instead. Since this app is pre-launch with no live frontend depending on it, this has no real-world impact.

4 new tests added covering the "caller has no matching profile" path for the three fixed self-lookup methods (previously silently masked by the dead fallback), on top of updating existing tests' fixtures to use profile ids where the endpoint semantics changed. Full suite: 450 tests passing.

**Not fixed (deliberately out of scope, per the chosen fix tier):** the internal caller-identity comparison pattern (`SecurityUtils.getCurrentUserId()` compared against `<entity>.getUser().getId()`) remains `User.id`-based everywhere, including in the now-converted modules - this is correct and consistent already (a JWT identifies a `User`, not a profile) and was never part of the inconsistency. Booking, quiz, progress, learning, and live modules were not touched - they were already on the profile-id scheme.

---

## Deeper Integration Tests (2026-07-17)

Before this, "test coverage" meant unit tests (mocked dependencies) and `@WebMvcTest` security-slice tests (controller layer with a mocked service) - nothing ever booted the real app and drove an actual multi-step business flow through real HTTP calls, a real database, and a real Redis. `SchemaValidationTest` was the only thing that booted a real Postgres, and only to validate the schema, not to exercise any endpoint.

**Built a shared integration-test harness** (`integration/AbstractIntegrationTest`):
- `@SpringBootTest` (full, non-sliced context) + `@AutoConfigureMockMvc`, so the complete servlet filter chain is active - real JWT authentication, real Redis-backed rate limiting - unlike `@WebMvcTest`, nothing is mocked here.
- Postgres and Redis as Testcontainers, started once via the "singleton container" pattern (a static initializer, not per-class `@Container` lifecycle) so every integration test class shares the same running containers and the same cached Spring context, keeping the suite fast as it grows.
- No active Spring profile, so `ddl-auto=validate` applies (same as prod, same rationale as `SchemaValidationTest`).
- Each test method runs inside a rolled-back transaction (`@Transactional`) so DB writes never leak between tests; Redis rate-limit counters aren't part of that transaction, so they're flushed explicitly in a `@BeforeEach` instead.
- Shared helpers: `login`, `loginFull`, `createSchool`, and a generic `parse(MvcResult, Class<T>)` that unwraps this app's `ApiResponse<T>` envelope into the real response DTO.

**Two flows built as the first pass** (per the agreed scope - the rest is intentionally left for follow-up passes, not attempted in one shot):

1. **`AuthIntegrationTest`** - the full auth lifecycle through real HTTP: log in as the bootstrap admin, create a school, register an instructor under that school, log in as the instructor, refresh the access token, log out, and confirm the now-revoked refresh token is rejected. Plus two negative-path checks: anonymous self-registration is rejected, and login with a wrong password is rejected.
2. **`BookingLifecycleIntegrationTest`** - the deeper cross-module chain: register an instructor and a student (plus a second, unrelated instructor for the negative-path checks), instructor books a lesson for their own student, a conflicting second booking is rejected, a student can't self-create a booking, an instructor can't create one under another instructor's identity, the student's automatic `IN_APP` notification appears via `GET /notifications/me` and can be marked read, the instructor authors a lesson note linked to the booking (verifying the profile-id scheme fix from the previous session in a real round-trip), the owning student can read it while an unrelated instructor cannot, and a genuine PDF attachment upload succeeds while a content-spoofed one is rejected (verifying the malware/content-validation fix). This single flow exercises most of the fixes made across this project's recent sessions together, in one realistic path, rather than in isolation.

**Not verified by actually running these two classes locally:** this machine's Docker Desktop (4.67.0, Engine API 1.54) has a client/server negotiation mismatch with the bundled Testcontainers/docker-java version that the earlier, simpler "wrong Postgres port" issue (documented above, in the booking-workflow section) turned out not to be - `docker info`/`docker compose` work fine directly, but the JVM's Docker client gets an empty, malformed response from the daemon no matter which named pipe or API version it's pointed at (tried both `DOCKER_HOST` override and pinning `DOCKER_API_VERSION`). This is the same class of problem that already blocks `SchemaValidationTest` locally on this machine, just confirmed here to run deeper than a simple port remap can fix. Both new test classes compile cleanly and the rest of the suite (450 tests) is unaffected by their presence; every HTTP call's expected status code and JSON shape was manually cross-checked line-by-line against the real controller/service/DTO code they exercise (not guessed) to substitute for actually running them locally. **Confirmed in CI on the very next push:** both classes ran for real against genuine Docker (4 tests, 0 failures) - the manual review held up, and this local machine's Testcontainers limitation is now conclusively just a local quirk, not a signal to distrust the tests.

**Third flow added: `QuizFlowIntegrationTest`** - course/quiz/question authoring through publish, draft-vs-published visibility (an unpublished quiz is invisible in the course's published list and denied to an unrelated student), scoring (a full-marks submission passes, an all-wrong resubmission doesn't), and `maxAttempts` enforcement (a third attempt beyond the configured limit is rejected). Also proves the identity-spoofing guard end-to-end: a student who submits with a *different* student's ID in the request body still gets scored under their own identity, never the claimed one - `QuizServiceImpl` ignores the body-supplied `studentId` for the `STUDENT` role and always resolves the caller's own profile. The `registerAndIdentify` helper (register via admin token, log in, resolve the new user's own profile id via `GET /auth/me`) was promoted from `BookingLifecycleIntegrationTest` into `AbstractIntegrationTest` so this and future flow tests share it instead of duplicating it.

**Confirmed in CI:** all three integration test classes ran for real against genuine Docker (5 tests total across `AuthIntegrationTest`/`BookingLifecycleIntegrationTest`/`QuizFlowIntegrationTest`, 0 failures) - full suite at 456 tests, green.

**Fourth flow added: `UploadsIntegrationTest`** - the full lesson-note attachment lifecycle, which `BookingLifecycleIntegrationTest` only touched in passing (one valid upload, one spoofed-content rejection). This one is dedicated to the attachment feature area itself: upload is role-gated (student can't upload at all) and ownership-gated (an unrelated instructor can't upload to someone else's note); a wrong file extension is rejected even with a valid PDF `Content-Type` header, and separately, content that doesn't actually match the claimed PDF type is rejected too; listing and downloading are read-access-gated (owning student/instructor can, an unrelated student or instructor can't) with a genuine byte-for-byte download round trip; replacing an attachment resets its download counter and swaps the file in place (same attachment id); and delete permission is scoped to the specific uploader (or ADMIN) - not just anyone who can manage the note - confirmed by having an unrelated instructor try and fail before the real uploader succeeds, followed by a 404 on the now-deleted attachment.

**Two wrong assumptions caught by CI, both fixed, neither an app bug:**
1. `GET /attachments/page` returns a `Page<T>` (`data.content`), unlike the plain `GET /attachments` list endpoint which returns a bare array (`data`) - the first draft used the same bare-array parser for both.
2. The download endpoint's `Content-Disposition` filename is the internal storage-generated name (a UUID-based path segment from `LocalStorageService`'s `UrlResource`), not the original uploaded filename - that original name only lives in the attachment's metadata response (`AttachmentResponse.fileName`), already verified separately. The test's assumption was wrong, not the app's behavior.

**Confirmed in CI** (after both fixes): all four integration test classes ran for real against genuine Docker - full suite at 457 tests, green.

**Fifth flow added: `LicenseWorkflowIntegrationTest`** - the full student license-progression workflow: initialization (and rejecting a duplicate), ownership-gated viewing (owning student can, an unrelated student can't), ownership-*and*-role-gated theory-progress tracking (an instructor is rejected outright - this endpoint is student/admin only, not a per-student ownership question at all - while an unrelated student is rejected on ownership), the auto-advance-to-`THEORY_COMPLETED` rule at 100% progress, the admin-only `quiz-passed` gate (an instructor is rejected even though instructors can otherwise manage this workflow elsewhere), automated sequential stage advancement, and the instructor-controlled final stages (`ROAD_READY`/`DVLA_PROCESSING`/`LICENSE_APPROVED`). The most interesting case: an ADMIN caller passes the `/advance` endpoint's role check but is then rejected by the *service layer* for lacking a real `InstructorProfile` to record as the approver - proving `@PreAuthorize` role gates and internal business-rule gates are two separate, both-necessary layers, not redundant ones. Also confirms `approvedByInstructorId` in the response is genuinely the acting instructor's profile id, and that an invalid backward stage transition is rejected once the workflow reaches `LICENSE_APPROVED`.

**Confirmed in CI:** ran for real against genuine Docker on the first push, no fix-up round needed this time - full suite at 458 tests, green.

**Integration coverage now spans:** auth lifecycle, the booking/notification/lesson-note/upload chain, quizzes, the dedicated uploads lifecycle, and license-workflow progress - the five flow areas originally scoped for this pass.

---

## Admin School Scoping (2026-09-29)

The school/admin ownership model (one owning admin per school, plus the unrestricted bootstrap admin) was only ever enforced inside the school module itself (`SchoolServiceImpl`/`SchoolAccessValidator`). Every other module's access checks still short-circuited on `role == ADMIN`, from before ownership existed - so a regular admin of school A could read and mutate school B's records just by passing B's IDs: create or soft-delete B's students/instructors, list B's rosters, suspend B's students, deactivate B's instructors, add/edit B's vehicles, create/view/confirm/cancel/complete B's bookings, send notifications to B's users, read B's lesson notes/questions/routes/assessments/license workflows/gamification, manage B's instructors' courses and quizzes, and see every school's data in the ADMIN-only "list everything" endpoints.

**Fix:** a single `AdminSchoolScope` component (`school/validator`) that every former ADMIN short-circuit now goes through:
- The bootstrap admin stays unrestricted; a regular admin is confined to the school they own (looked up on the `School.owningAdmin` FK side, same as the rest of the ownership code); a regular admin with no owned school is rejected rather than falling back to unrestricted.
- It's a no-op for non-ADMIN callers, whose existing per-module rules are unchanged - deliberately, since some shared service methods (e.g. `LicenseWorkflowService.markQuizPassed`, called from inside a student's own `QuizService.submit`) run in a STUDENT's request context.
- Single-record and write endpoints reject a cross-school request (400 from validators/services, matching the existing validator convention; 403 for bookings, whose checks live in `@PreAuthorize` via new `BookingSecurity.isAdminFor*` methods).
- The ADMIN-only listings (`GET /lesson-notes`, `GET /lesson-routes`, `GET /lesson-questions/status/{status}`) are filtered to the admin's school instead of rejected, via new school-filtered repository queries.
- `POST /notifications/send` checks the recipient's school through a new `NotificationService.sendAsCaller`; `send()` itself stays unchecked because it's also the internal system path (e.g. a regular admin's deletion request notifying the bootstrap admin, who is outside that admin's school).
- Published courses/quizzes/lessons stay readable by an admin of any school, since they're already readable by every authenticated role in every school - only drafts and management actions are scoped.

**Also fixed while in there:** `LiveSessionService.register` never checked that the student belongs to the session's school - for students too, not just admins. It now does. `LicenseWorkflowService.initializeForStudent` now checks school access before revealing whether a workflow already exists.

**Verified:** 65 new unit/security tests (a direct `AdminSchoolScopeTest`, an other-school rejection case beside every existing admin case, and the list-filter paths); the existing `admin_alwaysAllowed` tests were renamed to `adminOfSameSchool_allowed`, since "always" is no longer true. Full unit suite: 652 tests, green. New `AdminSchoolScopingIntegrationTest` drives the whole thing through real HTTP with two real schools and admins - school A's admin is rejected across accounts, profiles, vehicles, bookings, notifications and lesson notes, while school B's own admin and the bootstrap admin keep full access.

**Follow-up - the same gaps for instructors and students (fixed in the next commit):** a new `CallerSchoolScope` confines any caller (not just an admin) to their own school - an instructor or student to their profile's school, a regular admin to the one they own, the bootstrap admin to none. Closed with it: an instructor writing a lesson note or driving assessment for another school's student (which also unlocked reading that student's whole note/assessment history, since "has written one" is the read-access signal, and a PASSED assessment awarded them points); a student assigning a question to another school's instructor; any instructor answering or re-statusing an *unassigned* question from any school; an instructor scheduling a live session - and its meeting URL - under another school; `POST /notifications/send` letting an instructor message any user anywhere; `GET /lesson-questions/status/{status}` returning every school's questions to an instructor; the license workflow leaving instructors unrestricted across schools (still unrestricted *within* their school, by design); and `GET /vehicles/{id}` / `GET /vehicles/school/{id}` being readable for any school.

**Live-verified** against a freshly migrated Postgres 16 + Redis 7 (throwaway containers, the app booted from the built jar - all 15 migrations applied, `ddl-auto=validate` passed, and the new school-filtered JPQL parsed at startup): a 25-check HTTP smoke run mirroring the integration test passed in full. (`AdminSchoolScopingIntegrationTest` itself can't run on this machine for the same Testcontainers/Docker Engine API quirk documented above - CI is its real verification.)

**Found while verifying (unrelated, pre-existing - since fixed):** the `@Async` `SmsNotificationSender` (and likely the email sender, same pattern) runs before the caller's transaction commits, so when a booking-created SMS is dispatched it tries to update a `Notification` row that isn't committed yet and fails with `ObjectOptimisticLockingFailureException` - the SMS delivery record is left at `PENDING`. Only surfaces once real async dispatch races a still-open transaction; fixed by handing `@Async` senders the notification from an `afterCommit` transaction synchronization (`NotificationServiceImpl.dispatch`), which also means a rolled-back caller never sends a message about something that no longer exists. Synchronous senders (in-app, push) are unchanged.

---

## Disabled Accounts and Refresh Tokens (2026-09-29)

Login already refused disabled accounts (Spring's `DaoAuthenticationProvider` status check), but nothing after login re-checked: `POST /auth/refresh-token` never looked at `enabled`/`deletedAt`, so a soft-deleted or admin-disabled user holding a refresh token could keep minting access tokens indefinitely, and `JwtAuthenticationFilter` kept authenticating an already-issued access token for its full lifetime. A password reset also revoked nothing - a leaked refresh token stayed valid for its full 7 days even after the owner reset their password.

**Fixed:** the filter no longer authenticates a disabled/soft-deleted account's token; refresh rejects disabled/soft-deleted accounts; and a password reset revokes every refresh token already issued to that user via a per-user Redis marker (`revoked:refresh:user:{id}` = the reset's epoch second, TTL = the refresh-token lifetime, compared against the token's `iat` - strictly-before, in whole seconds, since JWT `iat` has one-second precision). Already-issued access tokens still run out their short lifetime (15 minutes by default) after a reset. 9 new tests; full unit suite 678 green.

---

## OpenRouteService Client Errors (2026-09-29)

Every 4xx from OpenRouteService - most commonly HTTP 404 "Could not find routable point within a radius of 350.0 meters" (error code 2010) when a user picks a coordinate off the road network - fell into the generic `RestClientException` handler: logged at ERROR (so each one became a Sentry event, the same noise problem as the expired-JWT logging fixed on 2026-08-01) and answered with a generic "check your coordinates" message that hid the API's actual explanation. Flagged by Sentry's own suggested fix on the `seer/fix/ors-404-error-handling` branch, which can't be merged directly (it predates `main`'s history rewrite).

**Fixed**, based on OpenRouteService's documented error codes rather than that branch's guess: 400 (2003 invalid parameter), 404 (2009 route not found / 2010 point not found) and 413 (2004 over a limit) are logged at WARN and answered with the API's own `error.message` (body shape `{"error":{"code":...,"message":...},"info":{...}}`, confirmed against a real reported response); any other 4xx stays an ERROR with a generic "try again later" message, since it isn't something the user's coordinates can fix. 3 new tests; full unit suite 687 green.

---

## Register No Longer Hands Out the New User's Tokens (2026-09-29)

`POST /auth/register` and `POST /auth/admin/register` returned the NEW user's access and refresh tokens to the admin or instructor who created the account - letting the creator (or anyone who could see that response) act as the new user for the refresh token's full 7 days without ever knowing their password. Confirmed with the frontend owner that the frontend doesn't read them. Both endpoints now return only the new user's identity (`user.id` / `user.email` / `user.roles`); `accessToken`, `refreshToken`, `tokenType` and `expiresIn` are omitted entirely (`AuthResponse` is now `@JsonInclude(NON_NULL)`, which leaves login's response unchanged since login always sets every field). No tokens are generated for the new account at all. New `AuthMapperTest` checks the serialized JSON of both shapes, and `AuthIntegrationTest` now asserts the register response carries no tokens. Full unit suite 689 green.

---

## Course Content Confined to Its School (2026-09-29)

Published courses, video lessons, resources and quizzes were readable by every authenticated user in every school - school A's students could browse, open and take school B's published courses and quizzes, and `GET /video-lessons/course/{id}` had no course access check at all. Confirmed with the product owner that content should be per-school, not a shared catalog.

**Fixed:** reading a course, lesson or quiz now requires the caller to be in the content's school (the owning instructor's), via `CallerSchoolScope` in `LearningValidator`/`QuizValidator`; the existing draft/owner rules still apply on top. `GET /courses` returns only the caller's own school's published courses (bootstrap admin: every school's) and `GET /quizzes/course/{id}` checks the course's school first (an unknown course is now a 404 rather than an empty list). Quiz submission also rejects a quiz from another school than the student's. Lessons-by-course gets only the school check - each lesson's own published flag still decides visibility within it, as the single-lesson endpoint already does.

Both cached lists stay correct under caching: the cached lookups moved into their own beans (`PublishedCourseCatalog`, keyed per school; `PublishedQuizCatalog`, keyed per course), because Spring's cache proxy only intercepts calls from outside a bean - the service resolves the caller's school or checks access on every call before asking the cache, so a cache hit can never skip the check or serve one school's list to another. Course changes now evict every entry of the `courses` cache (`allEntries`), since schools are few. 8 new tests plus content checks in `AdminSchoolScopingIntegrationTest`; full unit suite 693 green.

---

## Hosting Moved to Render (2026-09-29)

The backend now runs on Render (Docker web service + Render Postgres + Render Key Value), deployed from this GitHub repo. Render deploys `main` itself through its GitHub integration, so the Railway `deploy` job in `ci.yml` and `railway.json` are removed; with the service's Auto-Deploy set to **After CI Checks Pass**, every push to `main` deploys automatically once GitHub Actions is green, and nothing deploys when it isn't. `DEPLOYMENT.md` is rewritten for Render (service settings, Postgres/Key Value wiring, health check, auto-deploy, backups via point-in-time recovery and exports), and comments that named Railway/Cloud Run/GKE as the current host are updated.

**Worth checking on the live account (from Render's docs, not something the repo can see):** free Render Postgres databases expire 30 days after creation with no backups; free Key Value instances lose all data on restart (so refresh-token revocations are forgotten); the Key Value maxmemory policy should be `noeviction` so a revocation entry can't be evicted.

**Resolved (see "Rate Limiting Keyed to the Real Visitor" below):** the rate limiter's client-IP rule, settled empirically with the temporary echo endpoint, which has since been removed.

---

## Student-Instructor Messaging and Announcements (2026-09-29)

**The problem:** students' messages to instructors ended up with the admin. The only student-to-instructor channel was lesson questions, and students had no way to list their school's instructors (`GET /instructors/school/{id}` is admin-only) - so questions went out without `assignedInstructorId`, notified no instructor, appeared in no instructor's assigned/pending inbox, and only surfaced in `GET /lesson-questions/status/{status}`, the admin-facing listing. A question is also one question and one answer, not a conversation, and instructors had no way to reach all their school's students at once.

**Built (new `messaging` module, migration V16):**
- **Conversations** - one private thread per student-instructor pair of the same school, either side can open it (`POST /conversations`, idempotent), any number of messages both ways, an inbox with unread counts and last-message preview, newest-first paginated threads, mark-as-read, and an IN_APP notification to the recipient per message. `GET /conversations/contacts` lists who the caller can message (a student: their school's active instructors; an instructor: their school's students) - which also finally gives students an instructor picker for lesson questions. Admins can't use or read conversations. Deleted accounts and inactive instructors can't be messaged.
- **Announcements** - `POST /announcements` (instructors) reaches every student of the instructor's school in-app immediately and by email; one-way by design (students reply through a conversation). `GET /announcements` lists the caller's school's (bootstrap admin: all).
- **Announcement email goes through Resend's batch endpoint** (up to 100 per request, sent sequentially after commit) instead of one request per student - Resend's documented default rate limit is 10 requests/second per team, which per-student sends would exceed at a normal school size. Each student still gets their own EMAIL delivery record, marked SENT/FAILED per batch.
- All new foreign keys `ON DELETE CASCADE`, so the bootstrap admin's school/admin cascade-delete keeps working.

**Verified:** 30 new unit/security tests plus `MessagingIntegrationTest` (runs in CI); full unit suite 725 green. Live: V16 applied on a fresh Postgres 16 and passed `ddl-auto=validate`, and a 24-check HTTP smoke run passed - including the announcement's email batch reaching Resend's real `/emails/batch` endpoint after commit (rejected there only for the missing local API key, and both delivery records settled to FAILED rather than staying PENDING).

**Not built (needs a product/infrastructure decision - raised with the owner, not assumed):** realtime delivery. Clients poll; see the options discussed for WebSockets or an external realtime/push provider.

---

## Unassigned Lesson Questions Reach the School's Instructors (2026-09-29)

A lesson question sent without `assignedInstructorId` notified nobody and appeared in no instructor's inbox - only the admin-facing status listing. Per the product owner's choice, the instructor stays optional ("any instructor"), and now: every active instructor of the student's school gets an IN_APP notification; the question shows in each of their `GET /lesson-questions/pending` inboxes (new `findInstructorInbox` query: my pending questions plus my school's unclaimed ones) and is readable by them; and the first instructor to answer it or change its status claims it (becomes its instructor), so it drops out of everyone else's inbox. 5 new unit tests plus inbox/claim checks in `AdminSchoolScopingIntegrationTest`; full unit suite 730 green.

---

## Realtime Push over WebSockets (2026-09-29)

The frontend had to poll for new messages. Per the product owner's choice (WebSockets, no external realtime service), the backend now pushes events over STOMP on a plain WebSocket at `/ws`:

- **Auth**: browsers can't set headers on the handshake, so it's public and the access token goes in the STOMP CONNECT frame; `StompAuthChannelInterceptor` applies the REST API's checks (valid ACCESS token, existing, enabled account). A client may only SUBSCRIBE to its own `/user/queue/...` and may never SEND. Allowed origins = `CORS_ALLOWED_ORIGINS`.
- **Events** on `/user/queue/events`: `MESSAGE_CREATED` (to both participants, `mine` per receiver), `CONVERSATION_READ` (read receipts), `NOTIFICATION_CREATED` (every IN_APP notification), `ANNOUNCEMENT_CREATED`. `RealtimePublisher` sends only after the caller's transaction commits, never on rollback, and a failed push never breaks the request.
- 10s STOMP heartbeats, per Render's keepalive recommendation; clients reconnect with backoff after deploys (documented in the frontend guide).
- **Single-instance by design**: Spring's in-memory broker only reaches sessions on the same instance, and Render assigns WebSocket connections to random instances - keep the service at one instance, or add a Redis pub/sub relay before scaling out (`DEPLOYMENT.md`).

**Verified:** 17 new unit tests (interceptor, publisher, per-service events) plus `RealtimeWebSocketIntegrationTest` - the first real-port test in the suite: a real STOMP client over a real WebSocket against the running server, driven through the REST API (runs in CI; the Testcontainers holder was extracted into `IntegrationTestContainers` so both harnesses share one Postgres/Redis pair). Live against the built jar on a fresh Postgres + Redis: a 14-check Node STOMP client run passed - token/refresh-token/deleted-account rejection, subscription rules, heartbeat negotiation, every event type reaching the right user and no one else, ISO timestamps.

---

## Course Materials: PDF Upload, Edit and Delete (2026-09-29)

Instructors had no way to upload a PDF for their students: lesson-note attachments are real uploads but private to one student, and course resources (visible to every student of the school) only stored a pasted external URL, with no upload, no edit and no download endpoint. Per the product owner's choice, course resources now support real files, reusing the attachment storage path (`StorageService` - Cloudinary in prod - plus `FileValidator`'s PDF signature/active-content checks):

- `POST /resources/upload` (multipart: lessonId, title, file), `PUT /resources/{id}` (rename), `PUT /resources/{id}/file` (replace - new file stored first, old one deleted only after the change commits), `DELETE` (removes the stored file too, after commit), and `GET /resources/{id}/download` (`?inline=true` to view in the browser instead of saving), gated by the same school/published/owner rules as viewing the lesson.
- Migration V17: `file_url` becomes nullable and `storage_path`/`file_name`/`file_size`/`content_type` are added, with a check constraint that a resource is exactly one of a link or an uploaded file. Existing link resources are unchanged.
- The frontend guide now tells the frontend to use the OS file picker (device storage plus the cloud storage apps it already exposes) and drag-and-drop, how to view/download with the token (fetch + object URL), and lists provider-specific pickers (Google Picker, Dropbox Chooser) as optional external integrations needing the owner's approval.

**Verified:** 17 new unit/security tests; full unit suite green. Live against the built jar on a fresh Postgres + Redis (V17 applied): a 14-check run passed - students blocked from uploading/deleting, a spoofed non-PDF rejected, a real PDF uploaded, listed, downloaded byte-for-byte, served inline for viewing, blocked for another school's student and for anonymous callers, renamed, replaced (students get the new bytes), deleted - and no stored file left orphaned on disk.

---

## Rate Limiting Keyed to the Real Visitor (2026-09-29)

Measured against the live Render service with the temporary echo endpoint: `request.getRemoteAddr()` - what `RateLimitingFilter` keyed on - is a Cloudflare edge server, a different one on almost every request, so six requests from one machine landed in several counters (`X-Rate-Limit-Remaining` 99, 98, 98, 97, 97, 99): strangers shared limits and one client's requests were spread across many. `X-Forwarded-For` keeps whatever the client sends and only appends the real IP (so its first entry is forgeable); `True-Client-IP` is overwritten with the real IP; `X-Real-IP` is stripped; and `CF-Connecting-IP` always carries the real IP - a request that tries to set it is rejected by Cloudflare itself (HTTP 403, error 1000).

**Fixed:** new `ClientIpResolver` keys the limiter on a configured trusted header - `CF-Connecting-IP` in the prod profile (`RATE_LIMIT_CLIENT_IP_HEADER` to override) - falling back to the socket address when unset (local dev/CI, where any header could be forged). The temporary `GET /api/v1/diagnostics/client-ip` endpoint is removed. 5 new tests; full unit suite 768 green.

## Notifications Can No Longer Fail the Action That Triggered Them (2026-09-29)

**The bug:** every service that notifies as a side effect (bookings, messages, lesson questions, lesson notes, assessments, quizzes, registration, password changes, school creation, deletion requests) called `notificationService.send(...)` inside its own transaction, wrapped in a try/catch. That catch never worked - the trap recorded in item 8 of the booking section above. `send()` is `@Transactional` and joins the caller's transaction, so any exception inside it (a missing user, a constraint violation, an oversized subject...) marks the whole transaction rollback-only on the way out, and the caller's commit then throws `UnexpectedRollbackException`. The booking, message or account fails even though the exception was "caught". One concrete trigger: announcement notification subjects are built from user text (`"Announcement from <name>: <subject up to 200>"`) and could overflow the 300-character column.

**Fix:**
- New `NotificationService.sendAfterCommit`. It waits for the caller's transaction to commit (and sends nothing if it rolls back), then sends in a `REQUIRES_NEW` transaction of its own, and only logs a failure - it never throws.
- A `REQUIRES_NEW` send *inside* the caller's transaction wouldn't work: it can't see the caller's uncommitted rows, such as the brand-new user a welcome notification is for.
- All best-effort call sites use it. `POST /notifications/send` still uses `send()` directly, because there the notification *is* the request.
- `Notification` subjects are trimmed to 200 characters with an ellipsis, so they can't overflow the column.

**Tests:**
- Unit tests prove delivery is deferred until commit and uses `REQUIRES_NEW`, that nothing is sent on rollback, and that a failing send never throws.
- The booking and assessment "notification failure can't fail it" tests now assert the after-commit path.
- The MockMvc harness never commits, so the IN_APP notification assertions moved out of `BookingLifecycleIntegrationTest` and `MessagingIntegrationTest`. They now live in the new real-server `NotificationDeliveryIntegrationTest`, which checks welcome, booking (including mark-as-read) and message notifications after real commits.
- `AbstractRealServerIntegrationTest` is the shared base for real-server tests.

## Account Verification by One-Time Code (2026-09-29)

**What:** login now requires a verified account. The product owner chose the design:
- Codes are 6 digits, valid for 10 minutes, with 5 attempts and at most one send per 60 seconds.
- Email codes are sent through the existing Resend setup. Twilio Verify's own email channel requires SendGrid, a second email provider, so it wasn't used.
- WhatsApp codes go through Twilio Verify, and only to profiles with an international-format number.
- Phone numbers stay optional.

**Flow:**
1. `POST /auth/login` with the right password, for an unverified account, returns `verificationRequired` plus a challenge (an opaque random id in Redis, 15 minutes), the available channels and the masked destinations. It returns no tokens.
2. `POST /auth/verification/send` sends the code.
3. `POST /auth/verification/confirm` verifies the account and returns the normal login tokens.

A wrong password still gets a plain 401, so the flow can't be used to probe accounts.

**Who verifies:**
- V18 marks every existing account verified, so nobody who used the app before is locked out, and adds `users.phone_verified`.
- New student and instructor accounts, and new school-owning admins (previously created as `email_verified=true`), verify at their first login.
- The bootstrap admin is unchanged.

**How it works:**
- Email codes are stored only as a SHA-256 hash in Redis.
- The attempt limit and cooldown are enforced in Redis for both channels.
- A failed delivery (503) doesn't start the cooldown.
- A code dies on its 5th wrong attempt.
- The new endpoints are public, rate limited like the other auth endpoints, and permitted in `SecurityConfig`.

**Configuration:**
- `RESEND_API_KEY` and `MAIL_FROM` are now effectively required in production: without them, a new user can only verify by WhatsApp.
- `TWILIO_VERIFY_SERVICE_SID` (with the existing SID and token) turns WhatsApp on.
- `VERIFICATION_LOG_CODES` logs codes instead of emailing them. It's on only in the `dev` profile and must never be set in prod.

**Verified:**
- 14 new unit tests.
- New `AccountVerificationIntegrationTest`: challenge instead of tokens, emailed code, 429 on early resend, wrong code, success, single-use challenge, later logins direct, school owner verifies, wrong password gets no challenge.
- A 29-check live smoke run of the built jar against fresh Postgres and Redis: V18 applied, 5-miss lockout, and the auth rate limit on the new endpoints.
- Existing integration tests mark the accounts they create as verified before logging in; the flow itself has its own test.
- Full unit suite: 787 tests, green.

## Verification Switch Until a Verified Email Domain Exists (2026-09-29)

Without its own domain, Resend only delivers from `onboarding@resend.dev` to the Resend account owner's address. So with verification required, no other new account could ever log in. The product owner chose an on/off switch: `VERIFICATION_REQUIRED` (`app.verification.required`, default `true`).

- **When `false`:** login returns tokens for unverified accounts without a challenge, and the app logs a warning at startup. Accounts stay unverified, so each one verifies at its next login once the switch is back on. That way every real account's email still gets checked eventually.
- **Going live:** the steps are in `DEPLOYMENT.md` ("Going live with account verification").
- **Tests:** 1 new unit test. Full unit suite: 788 tests, green.

## Frontend-Reported Issues (2026-09-30)

The frontend team tested against the live API and reported 18 issues. Where a choice was needed, the product owner decided.

**Fixed (bugs):**
1. **PDF uploads always 500.** There was no multipart setting, so Spring's defaults (1 MB per file, 10 MB per request) rejected any real PDF before the 50 MB check in `FileValidator` ran, and the resulting `MaxUploadSizeExceededException` fell through to the catch-all 500. `spring.servlet.multipart` is now 50 MB, and an oversized upload gets 413.
2. **Students couldn't see their own lesson's route.** The guide promised it, but `RouteValidator` only allowed admins and the owning instructor. The student the booking is for can now read it.
3. **Future lessons could be completed.** A lesson scheduled for 15 Nov was completed on 29 Sep, awarding points and badges. `complete` now rejects a lesson before its `scheduledAt` (400).
4. **A second route for a booking broke the lookup.** `findByBookingId` returned a non-unique result, giving a 500. Generating again now replaces the booking's route, and the lookup takes the newest row, so rows created earlier can't cause a 500 either.
5. **Request-shape errors returned 500.** Missing query parameter, missing file part and malformed JSON are now 400; an unsupported method is 405 with `Allow`; a wrong content type is 415.
6. **Access denials returned 400.** The product owner chose 403 everywhere. There's a new `ForbiddenException`, and 53 ownership / other-school / role checks now throw it; bad input and state conflicts stay 400.
7. **`POST /auth/register` without a token** returned a validation 400 (the endpoint was `permitAll` with `@PreAuthorize` inside). It's no longer public, so it gets 401 first.

**Built (requests):**
- **Live sessions:**
  - A student gets no `meetingUrl` until they register; every session has `registered` for students and `endsAt`.
  - The register response carries the link.
  - Sessions stay listed until they end, and late registration is allowed until the end.
  - New `DELETE /live-sessions/{id}/register`.
  - `durationMinutes` is capped at 24 hours, which keeps "still running" bounded.
- **`videoUrl` optional** (V19): a lesson can be materials only. On update, `""` removes the video.
- **`GET /lesson-routes/me`:** a student's routes, paginated.
- **`GET /courses?includeDrafts=true`:** every course of an admin's school, drafts included (403 for other roles).
- **Guide:** the CORS section no longer says localhost is allowed. The status table, 403 notes and every changed endpoint are updated.

**Configuration, not code:**
- **CORS:** add `http://localhost:5173` to `CORS_ALLOWED_ORIGINS` on Render if the frontend develops against the live API.
- **Email verification:** waits on a verified email domain (see "Going live with account verification").
- **Render free-tier sleep:** the owner chose to stay on the free plan for now. Free Postgres expires 30 days after creation.

**Test data (item 10):** cleanup SQL was given to the owner to run by hand. It checks every assumption and changes nothing if one fails.

**Verified:** the unit suite and local integration tests (Testcontainers now works locally with `DOCKER_API_VERSION=1.44` and `-DargLine=-Dapi.version=1.44`, because Docker 29 rejects the client's default API version), then CI.

## PDF Uploads: Strip Active Content Instead of Rejecting It (2026-10-01)

**The problem:** the frontend found that ordinary PDFs from Word, Google Docs and LaTeX were refused. `FileValidator` searched the raw bytes for `/OpenAction` and `/AA` and rejected any match. Those keys normally hold harmless actions such as "open at page 1" or "fit width". The check was also bypassable in the other direction: in a PDF that uses compressed object streams (most modern ones), a byte search can't see `/JavaScript` at all.

**Fix:** new `PdfSanitizer`, using Apache PDFBox 3.0.7. It parses the document and walks its object graph, so compressed objects are covered too, and removes only active content:
- **Always removed:** JavaScript (as actions, as document-level name trees, and as `/JS` keys on any object); Launch, SubmitForm, ImportData, RichMediaExecute, GoToE and Rendition actions, including inside `/Next` chains; embedded files (`/EmbeddedFiles`, `/EF`) and portfolios; XFA forms; and file-attachment, media and 3D annotations.
- **Removed only when automatic:** URI and GoToR actions that run on open or through `/AA` triggers. The same actions behind a link the reader clicks are kept.
- **Kept:** harmless open actions, links and destinations.
- **Unchanged files:** a PDF with nothing to remove is stored byte-for-byte unchanged; it's only re-saved when something was stripped.
- **Rejected with a clear 400:** password-protected PDFs and files PDFBox can't parse.
- **Storage:** `FileValidator.validate` now returns the bytes to store. All three storage backends store, hash and size those bytes, not the raw upload.

**Tests:** `PdfSanitizerTest` uses real PDFs generated by PDFBox (`TestPdfs`):
- a plain file and a "fit width on open" file are untouched;
- a JavaScript open action, a document-level script, a launch link and an embedded file are stripped, with the rest kept;
- a password-protected file and a fake PDF are rejected.

Tests that uploaded the fake `"%PDF-1.4\n%%EOF"` now use real PDFs. Unit suite: 825 tests, green.

## Daily Attendance with Location Check-In and Excel Registers (2026-10-02)

Frontend tracker #5, plus the product owner's request for printable attendance sheets. Decisions: per-school time zone; student check-ins confirmed by an instructor or the admin of the school; LATE is manual only; students and instructors check in.

**Migration (V20):**
- `schools` gains `latitude`, `longitude`, `attendance_radius_meters` (default 150) and `time_zone` (default `Africa/Accra`).
- New `daily_attendance` table (separate from live-session `attendances`) holding status, source, the check-in location, accuracy and distance, who confirmed or recorded it, and a reason. `UNIQUE (user_id, attendance_date)` enforces one record per person per school-local day.

**Check-in:**
- Rejected if the school hasn't set a location.
- Rejected if accuracy is worse than 100 m, or if the haversine distance is beyond the radius. Both rejections are a 400 with details: `accuracyMeters` or `distanceMeters`/`radiusMeters`, via the new `DetailedBadRequestException`.
- Rejected on a second check-in the same day.
- Students start as `PENDING_CONFIRMATION`; instructors are `PRESENT`.

**Staff:**
- Confirm a pending check-in.
- Record or correct a day manually (PRESENT/LATE/ABSENT; instructors only for students of their own school; no future dates).
- Day list including people without a record (`NOT_CHECKED_IN` today, `ABSENT` for past days).
- A person's history.
- Excel exports built with Apache POI 5.5.1: a register (people x days, up to 62 days, P/L/A/? marks with totals) and a one-day detailed list. Both are laid out for printing.

**Also:** `Clock` is now a bean (`ClockConfig`) so date rules can be tested.

**Tests:** 22 new (`AttendanceServiceTest`, `AttendanceExportServiceTest`). The export test opens the generated workbook and checks cells and print setup. Unit suite: 853 tests, green.

## Session Limits: "Keep Me Signed In" and an Inactivity Timeout (2026-10-02)

Frontend tracker #10. Logins used to last up to 7 days on any computer (a 7-day refresh token re-issued on every refresh), which is risky on shared school computers. The product owner chose "remember me" plus a server-enforced idle timeout.

**Rules:**
- Login (and `/auth/verification/confirm`) takes an optional `rememberMe`.
- A session lasts at most 12 hours without it and 7 days with it. Admin sessions are capped at 1 day either way.
- A session that isn't refreshed for 2 hours ends.

**How:**
- The refresh token now carries its session: `sid`, start time `sst` and `rmb`.
- Every refresh token expires at `min(now + idle timeout, session start + session maximum)`.
- A refresh continues the same session, so activity extends it by the idle timeout but never past its maximum, and 2 hours without a refresh lets the token expire.
- Refresh tokens issued before this change have no session claims. They count as a "remember me" session that started when they were issued, so nobody is logged out by the deploy.
- All four limits are configurable (`JWT_REFRESH_EXPIRATION_MS`, `JWT_IDLE_TIMEOUT_MS`, `JWT_SESSION_MAX_MS`, `JWT_ADMIN_SESSION_MAX_MS`).

**Tests:** five `JwtTokenProviderTest` cases (idle expiry, the 12-hour and admin caps, the session travelling in the token, old tokens), plus `AuthServiceImplTest` checks that login starts a "remember me" session and that a refresh continues the same session.
