# Deployment

This app deploys to [Railway](https://railway.com). CI builds and tests on every push; a
deploy step in `.github/workflows/ci.yml` then ships `main` to Railway automatically once
it's green. Until the one-time setup below is done, that deploy step detects the missing
config and skips itself rather than failing the build.

## One-time setup

### 1. Create the Railway project

1. In the Railway dashboard, create a new project from this GitHub repo.
2. Railway will detect `project-with-changes/Dockerfile` automatically (via
   `project-with-changes/railway.json`, which also sets the healthcheck path and restart
   policy). If it asks for a root directory, set it to `project-with-changes`.
3. Add a **Postgres** plugin to the same project (one click, "New" → "Database" →
   "PostgreSQL"). This creates a second service in the project alongside the app.
4. Add a **Redis** plugin the same way ("New" → "Database" → "Redis"). Used as the
   refresh-token revocation store (logout / soft-delete both need to invalidate an
   already-issued refresh token, and Redis's key TTL support means revocation entries
   expire themselves in step with the token they revoke - no cleanup job needed).

### 2. Configure the app service's environment variables

In the app service's "Variables" tab, set:

| Variable | Value | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates `application-prod.yml` |
| `DB_HOST` | `${{Postgres.PGHOST}}` | Railway variable reference to the Postgres service |
| `DB_PORT` | `${{Postgres.PGPORT}}` | |
| `DB_NAME` | `${{Postgres.PGDATABASE}}` | |
| `DB_USERNAME` | `${{Postgres.PGUSER}}` | |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` | |
| `REDIS_HOST` | `${{Redis.REDISHOST}}` | Railway variable reference to the Redis service |
| `REDIS_PORT` | `${{Redis.REDISPORT}}` | |
| `REDIS_PASSWORD` | `${{Redis.REDISPASSWORD}}` | |
| `JWT_SECRET` | a random string, 32+ characters | e.g. `openssl rand -base64 48` |
| `BOOTSTRAP_ADMIN_ENABLED` | `true` for the very first deploy only | Flip back to `false` after you've logged in once and changed the password |
| `BOOTSTRAP_ADMIN_EMAIL` | your admin email | |
| `BOOTSTRAP_ADMIN_PASSWORD` | a strong password | Change it after first login regardless |
| `CORS_ALLOWED_ORIGINS` | your real frontend origin(s) | Comma-separated, no trailing slash |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | your SMTP relay's details | Needed for password-reset emails to actually send |
| `PASSWORD_RESET_URL` | your frontend's reset-password page URL | e.g. `https://yourapp.com/reset-password` |
| `STORAGE_PROVIDER` | `cloudinary` (default in prod), `gcs`, or `local` | **See the storage section below before going live.** |
| `CLOUDINARY_URL` | `cloudinary://<api_key>:<api_secret>@<cloud_name>` | Required when `STORAGE_PROVIDER=cloudinary`. Copy this directly from the Cloudinary dashboard ("API Environment variable") - it's the one variable Cloudinary's SDK needs, no separate cloud_name/key/secret fields. |
| `SENTRY_DSN` | your Sentry project's DSN | Optional - error tracking stays off (no-op) if unset. Get a DSN from [sentry.io](https://sentry.io) (or self-hosted Sentry). |
| `SENTRY_TRACES_SAMPLE_RATE` | a number 0.0-1.0, default `0.1` | Fraction of requests to trace for performance monitoring; only matters if `SENTRY_DSN` is set. |

`PORT` is injected by Railway automatically and is already wired up (`application-prod.yml`
reads `${PORT:8080}`) - don't set it yourself.

**Storage:** the app supports three providers via `STORAGE_PROVIDER`:

- **`cloudinary`** (the default in prod) - uploaded files (lesson-note attachments,
  currently PDF only) are stored on Cloudinary as `resource_type=raw` under the
  `authenticated` delivery type, meaning the raw asset URL alone can never fetch a file -
  only a signed URL this app generates (with its own API secret) can. This app's own
  permission checks (who's allowed to download a given attachment) still happen first, in
  the application layer, before a signed URL is ever generated - Cloudinary's
  authenticated delivery type is defense in depth on top of that, not a replacement for
  it. Needs just `CLOUDINARY_URL` (see the table above).
- **`gcs`** - Google Cloud Storage. Needs a real GCP project/bucket/credentials
  (`GCS_BUCKET_NAME`, optionally `GCP_PROJECT_ID`) - not set up by default.
- **`local`** - writes to `./uploads` inside the container, which does **not** persist
  across redeploys or restarts unless you attach a Railway
  [volume](https://docs.railway.com/guides/volumes) to that path. Blocked outright when
  the `prod` profile is active (`StorageProperties` fails fast at startup) - it's a
  dev/test-only option, never a real choice for a live deployment.

### 3. Get a Railway Project Token

In the Railway project's settings, under "Tokens", create a **Project Token** (scoped to
this project + environment - not your personal account token). Copy it.

### 4. Add GitHub repo secrets/variables

In this repo's GitHub settings → Secrets and variables → Actions:

- **Secret** `RAILWAY_TOKEN` = the project token from step 3.
- **Variable** `RAILWAY_SERVICE_NAME` = the exact name of the app service as shown in the
  Railway dashboard (not the Postgres service).

Once both are set, the next push to `main` that passes CI will deploy automatically.

### 5. (Optional) Require manual approval before deploying

The deploy job runs under a GitHub Actions `environment: production`. If you want a human
to approve each production deploy rather than deploying automatically on green CI, create a
"production" environment in this repo's Settings → Environments and add required reviewers
there - no workflow changes needed.

## How the pipeline works

`test` → `docker-build` → `deploy`, in that order, all gated by `needs:`. `docker-build`
only builds the image to prove the Dockerfile still works (matches what Railway itself will
build) - it doesn't push anywhere. `deploy` runs `railway up` from
`project-with-changes/`, which builds from the same Dockerfile and deploys it. It only runs
on pushes to `main` (never on pull requests), and only after both prior jobs succeed.

## Observability

- **Metrics**: `/actuator/prometheus` exposes Micrometer/Prometheus-format metrics in
  prod. It's still behind `SecurityConfig`'s `/actuator/** -> hasRole('ADMIN')` rule, so
  a real Prometheus server needs either network-level access to the app (e.g. both
  running on Railway's private network) or a scrape credential with the ADMIN role -
  it isn't openly scrapeable just because the endpoint is enabled.
- **Logs**: prod logs are structured JSON (Elastic Common Schema) via Spring Boot's
  built-in structured logging, not the old plain-text pattern - point a log aggregator
  (Loki, ELK, Cloud Logging, etc.) at stdout or `logs/driving-school-backend.log` and it
  parses fields directly instead of regexing a line. Every MDC key - including
  `requestId`, already populated per-request by `ApiVersioningFilter` - is folded into
  each JSON line automatically, so request correlation across log lines still works the
  same way it always did. Dev/local logs are unchanged (still human-readable).
- **Error tracking**: Sentry integration is wired in but inert until `SENTRY_DSN` is
  set (see the table above) - deliberately not defaulted to an empty string, since that
  specific case throws at startup instead of disabling cleanly.

## Secrets management

All secrets (`JWT_SECRET`, `DB_PASSWORD`, `SENTRY_DSN`, etc. - the full list is the table
in step 2 above) are stored as encrypted Railway environment variables, scoped per-service
- never committed to the repo, never visible in build logs.

**Rotation**: update the value in Railway's dashboard, then redeploy (Settings → Variables
→ Deploy). No code change needed for any secret in the table above.

**If you outgrow this** - multiple environments needing centrally-managed/audited secrets,
automatic rotation, dynamic short-lived database credentials - the next step is HashiCorp
Vault or a cloud provider's secrets manager (AWS Secrets Manager, GCP Secret Manager).
That's deliberately not set up now: it requires running infrastructure this project doesn't
have yet, and would be premature complexity for a single-environment Railway deployment.
Revisit if/when that changes.

## Backups

Railway's Postgres plugin supports both on-demand and scheduled backups from its
dashboard (Postgres service → "Backups" tab) - this project doesn't run its own backup
tooling, since duplicating what the platform already does well would be pure overhead.
That tab being available isn't itself a backup *strategy* though, so here's the concrete
one for this project:

1. **Turn on scheduled backups** for the Postgres service, not just on-demand ones.
   Daily is the practical minimum for a system tracking bookings, quiz submissions, and
   driving assessments - losing a day of that data to an unnoticed bad deploy is a real
   cost, not a hypothetical one. Pick the longest retention window your Railway plan
   offers that you're comfortable paying for; retention options and pricing are a
   dashboard/plan detail that can change, so check what's actually offered rather than
   assuming a specific number here.
2. **Always take a manual on-demand backup immediately before a risky migration** (see
   the migration-rollback runbook below) - scheduled backups cover the general case, but
   don't rely on the schedule happening to line up with the one deploy that actually
   needed it.
3. **To restore**: Postgres service → "Backups" → select a snapshot → restore. Railway
   restores into a fresh instance rather than overwriting the live one in place, so after
   restoring, update the app service's `DB_HOST`/`DB_PORT`/etc. variables (step 2 above)
   to point at the restored instance if Railway assigns it new connection details, then
   redeploy and verify `/actuator/health/readiness` before considering the incident
   closed.
4. **Test a restore at least once before you actually need it.** A backup nobody has ever
   restored from is a hope, not a plan - the failure mode (a snapshot that turns out to
   be corrupt, or connection details that don't work the way you expected) is much better
   discovered during a calm dry run than during an actual incident.

## Migration rollback

Flyway (community edition, which this project uses) has no native "undo migration". Once
`V<N>` has run against a real database, the only way back is a new forward migration that
reverses it - there is no automatic revert. `application-prod.yml` sets
`validate-on-migrate: true`, so if a deployed migration's checksum doesn't match what's on
disk, the app fails to start rather than silently applying something unexpected - that's
your safety net for *drift*, not for *unwanted-but-successful* migrations.

If a migration reaches production and turns out to be wrong:

1. **Don't panic-edit the applied migration file.** Its checksum is already recorded in
   `flyway_schema_history`; editing it after the fact just breaks Flyway on every other
   environment that already ran it (this project's own `validate-on-migrate: false` in
   the base `application.yml` is what lets local dev tolerate that kind of edit - prod
   does not).
2. **Write a new migration that reverses the change** (e.g. `V<N+1>__revert_x.sql`),
   following the same pattern already used in this repo's history (see V5, V7, V8, V9 -
   each one fixes forward, never edits a prior file). If the bad migration dropped a
   column with real data in it, that data is gone unless you have a database backup or
   snapshot from before it ran - which is why the next point matters.
3. **Take a database backup/snapshot before any migration you're not 100% sure about.**
   Railway's Postgres plugin supports on-demand and scheduled backups from its dashboard.
   For anything destructive (dropping a column, an `ON DELETE CASCADE` chain, a data
   rewrite), take a manual backup immediately before deploying, not after something goes
   wrong.
4. **If the app won't start because of a bad migration**, the fastest recovery is usually:
   restore the pre-migration backup, then fix the migration file itself (safe *only*
   because, if it never successfully completed and was never recorded as applied in
   `flyway_schema_history` on any other environment, editing it isn't the "breaks other
   environments" case from point 1) and redeploy.
5. **For anything nontrivial, test the migration against a copy of production data
   first**, not just an empty dev database. Every schema-drift bug fixed earlier in this
   project's history (see `PRODUCTION_READINESS.md`) was only caught by booting against a
   real, non-trivial database state - an empty database hides a lot.
