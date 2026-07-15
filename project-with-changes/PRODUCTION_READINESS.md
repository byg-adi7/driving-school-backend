# Production Readiness TODO

Compiled 2026-07-15 from a full audit of the codebase (security, data layer, test coverage, ops/deployment). Reflects state after the V8/V9 schema-alignment migrations landed on `main`.

**Overall assessment:** the app will now boot cleanly against a fresh production database, which it would not have a few commits ago. That fixes one specific, serious class of bug. It is not otherwise production-ready — there's no deployment pipeline, no way to catch a future version of the same schema-drift bug, a real security gap in the API docs exposure, and at least one functional gap (password reset) that real users will hit immediately.

---

## Before real users touch this (true blockers)

1. **Swagger/OpenAPI is publicly exposed in prod** — `SecurityConfig.java:143-144` permits `/swagger-ui/**` and `/v3/api-docs/**` unconditionally, despite a comment claiming it's admin-only. Anyone can see the entire API surface (endpoints, DTOs, field names) unauthenticated. Gate it behind the existing `isProduction` check, or set `springdoc.api-docs.enabled=false` in `application-prod.yml`.
2. **No password-reset / forgot-password flow.** Grepped `src/main/java` — nothing. Users who forget their password have no recovery path. This is a functional gap most real users will hit, not just a security nicety.
3. **No deployment pipeline.** `.github/workflows/ci.yml` runs tests and builds a Docker image with `push: false` — nothing pushes to a registry or deploys anywhere. There is no staging/prod environment definition in the repo at all.
4. **No migration rollback plan.** Flyway community edition has no native "undo," and this session already shipped two corrective migrations (V8, V9) for the same root cause (entities redesigned without a matching migration). `validate-on-migrate: true` in prod will at least fail fast on drift, but there's no written runbook for what happens when a bad migration reaches production.
5. **Local file storage has no production guard rail.** `application-prod.yml` defaults to GCS (correct), but nothing stops a misconfigured deploy (e.g. `STORAGE_PROVIDER` simply unset) from silently falling back to local disk storage, which won't persist or work across multiple instances. Add a startup-time check that rejects `local` when the active profile is `prod`.
6. **No test would catch a repeat of the V8/V9 bug.** Every existing test mocks the repository layer; nothing boots the full Spring context against a real database with `ddl-auto=validate`. This is exactly why the schema drift fixed this session went unnoticed for as long as it did. Add one `@SpringBootTest` (Testcontainers Postgres, or reuse the docker-compose Postgres) that boots the app and asserts a clean start — this is the single highest-leverage test in the whole suite.
7. **Confirm whether vehicle/instructor-profile/student-profile management is actually missing, or just handled elsewhere.** Those packages contain only entity + repository — no service or controller layer, so no way to manage vehicles or edit a profile via the API today. Either this is a real functional gap or it's intentionally out of scope for this phase; worth a five-minute conversation before assuming either.

---

## Security

| Priority | Item | Notes |
|---|---|---|
| High | Swagger exposed in prod | See blocker #1 above |
| Medium | No refresh-token revocation | Stateless JWTs, 7-day refresh default, no logout-time invalidation or denylist. Decide: short-lived refresh + rotation, or a revocation store. |
| Medium-High | No password-reset flow | See blocker #2 above |
| Low-Medium | Rate limiting is narrow | `RateLimitingFilter` only covers `/auth/login`, `/register`, `/refresh-token` (10 req/min, one shared bucket — not per-IP/per-user, so one abusive client can exhaust the budget for everyone). Nothing else in the API is throttled (booking creation, quiz submission, etc.). |

**Already solid, no action needed:** JWT secret enforces a ≥32-char minimum; BCrypt strength 12; CORS fully externalized with a real prod default (no wildcard); prod disables stack traces and binding-error leakage; actuator restricted to `health,info`; bootstrap-admin creation disabled by default in prod; `@PreAuthorize` with ownership checks consistently present on spot-checked controllers; request DTOs use `@Valid`; dependency versions (Spring Boot 3.4.5, jjwt 0.12.6, springdoc 2.8.6) are current.

---

## Data layer & schema

| Priority | Item | Notes |
|---|---|---|
| High | Cascade-delete chains, no soft-delete | Deleting a `User` cascades through `student_profiles`/`instructor_profiles` and destroys bookings, quiz submissions, assessments, and lesson notes with no recovery. No delete endpoint exists yet for users/students/instructors, so this is dormant — but a landmine for whoever adds one. Decide the policy (soft-delete flag, restrict-delete, or accept data loss) up front. |
| High | V9's `ADD COLUMN IF NOT EXISTS ... NOT NULL` pattern can silently skip tightening | On a database that already had the column (via a prior `ddl-auto=update` run) but nullable, `IF NOT EXISTS` causes the whole clause — including `NOT NULL` — to no-op. Confirmed for `vehicles.color`/`status`, `student_profiles.status`, `notifications.channel`/`status`, `license_workflows.theory_progress_percent`/`road_training_hours`/`stage_updated_at`. Fine on a fresh DB; worth an explicit follow-up `ALTER COLUMN ... SET NOT NULL` pass on any environment that predates V9. |
| Medium | Two entity-declared indexes were never created | `VideoLesson`'s composite `idx_video_lessons_order (course_id, lesson_order)`, and `School`'s `idx_schools_name` / `idx_schools_active`. Hibernate's `validate` mode doesn't check indexes at all, so this never surfaced. Lesson ordering and school lookups will do sequential scans as data grows. |
| Medium | Nullable-in-DB vs. `nullable=false`-in-entity mismatches | `video_lessons.video_url`/`lesson_order`, `quizzes.passing_score`, `quiz_questions.question_type`/`correct_answer`, `quiz_submissions.status`, `attendances.status`, `lesson_question_submissions.updated_at`, `notifications.body`, plus `learning_resources`/`live_sessions` title/timing columns on any DB that took the hybrid-rename path. None of these crash the app (Hibernate `validate` doesn't check nullability), but the DB will silently accept nulls the entity layer assumes can't exist. |
| Medium | Sparse `@EntityGraph`/`JOIN FETCH` usage | Only 2 of many repositories use fetch joins, despite several `@OneToMany(fetch = LAZY)` associations (`Course.lessons`, `Quiz.questions`, `Vehicle.locations`, `LiveSession.attendances`). No confirmed N+1 in a static pass, but any list endpoint serializing these collections is a likely candidate — worth checking with SQL logging enabled on the courses/quizzes/vehicles/live-sessions list endpoints. |
| Low | Varchar length mismatches | Entity vs. DB length differs in several places (e.g. `video_lessons.title` is VARCHAR(300) in the DB vs. `length=200` on the entity). DB column is always the wider one, so harmless — just inconsistent documentation of the real constraint. |
| Low | Timestamp/timezone discipline | All timestamp columns are timezone-naive, paired with `hibernate.jdbc.time_zone=UTC` and `LocalDateTime` fields. Valid pattern, but only if every write path is disciplined about UTC — worth a spot-check for any `LocalDateTime.now()` call running under a non-UTC server default. |

**Already solid:** transaction boundaries are consistent — every service-layer method checked has `@Transactional`/`@Transactional(readOnly = true)` where expected.

---

## Test coverage & quality

| Priority | Item | Notes |
|---|---|---|
| High | No integration/DB tests exist at all | Every test mocks the repository layer (Mockito), except one `@WebMvcTest`. Nothing uses `@SpringBootTest`, `@DataJpaTest`, Testcontainers, or H2 (H2 is a declared but entirely unused dependency). This is the root cause of blocker #6 above. |
| High | No migration/boot-validation test | Same gap as blocker #6 — flagged again here because it's the top item in this category specifically. |
| High | No service/controller layer for vehicles, instructor profiles, or student profiles | See blocker #7 above — consequently zero tests for these modules too, because there's nothing to test. |
| Medium | Security testing is inconsistent | Every controller uses `@PreAuthorize`, but only `BookingController` has a dedicated `*SecurityTest` proving those checks are enforced at the HTTP layer. Quiz, LessonNote, LiveSession, LicenseWorkflow, Notification, Role, School, LessonQuestion, and PracticalLessonRoute controllers have no equivalent. |
| Medium | `LessonQuestionSubmissionService` has zero tests | Its sibling `LessonQuestionStatusHistoryService` is tested; the submission service (handles student Q&A writes) is not. |
| Medium | No static analysis or coverage tooling | No Jacoco, Checkstyle, PMD, or SpotBugs configured. There's no way to know actual coverage % or catch code smells automatically. |

**For contrast, not a gap:** `QuizServiceImplTest` and `BookingServiceImplTest` are well-written — clear ownership/authorization-focused test names, reasonable branch coverage. This is the bar the untested modules should be held to.

---

## Ops & deployment

| Priority | Item | Notes |
|---|---|---|
| High | No deployment pipeline | See blocker #3 above. |
| High | No migration rollback strategy | See blocker #4 above. |
| High | Local storage has no production guard rail | See blocker #5 above. |
| Medium | No metrics/APM | Only `spring-boot-starter-actuator` on the classpath, no Prometheus registry or equivalent. No error-tracking integration (Sentry or similar). An outage today would be diagnosed from logs alone. |
| Medium | No secrets manager | `JWT_SECRET`, `DB_PASSWORD`, `BOOTSTRAP_ADMIN_PASSWORD`, storage credentials are all plain environment variables — no Vault/AWS Secrets Manager/GCP Secret Manager integration anywhere in config or code. May be fine if the deploy target injects these securely (e.g. Cloud Run + Secret Manager-backed env vars), but that binding isn't shown anywhere in this repo. |
| Medium | Logs are plain-text, not structured | Both `application-prod.yml` and `application-dev.yml` use a plain pattern layout, not JSON — harder to query in a log aggregator at scale. Request-ID correlation is already implemented well via `ApiVersioningFilter` populating MDC, so this is only about format, not missing functionality. |
| Low | `docker-compose.yml` is dev-only | Hardcodes `SPRING_PROFILES_ACTIVE: dev` and `STORAGE_PROVIDER: local`, no resource limits or restart policy on either service. Fine for local dev; there's no equivalent prod-shaped manifest (k8s, Cloud Run service definition, etc.) anywhere in the repo yet. |
| Low | No backup strategy for the Postgres data volume | Irrelevant if prod uses a managed DB service, but nothing in-repo states that assumption explicitly. |
| Low | Stale comment in `Dockerfile` | Says "no CI pipeline exists yet" — CI now exists. Harmless, just misleading to a future reader. |

**Already solid:** Dockerfile is well-built (multi-stage, non-root user, container-aware JVM memory sizing, correct health-check); `application-prod.yml` already correctly disables bootstrap-admin creation by default and restricts actuator/health detail exposure.

---

## Suggested order of attack

1. Fix the Swagger exposure (small, high-impact, low-effort).
2. Add the schema-validation boot test (prevents the exact bug class this session fixed from recurring silently).
3. Decide and implement the password-reset flow.
4. Stand up a minimal deploy pipeline (even just: push image to a registry + manual deploy step) and write the migration-rollback runbook.
5. Add the local-storage production guard rail.
6. Then work through the Medium items by module as time allows — the data-layer nullability list and missing indexes are cheap, mechanical fixes once someone's back in a migration file.
