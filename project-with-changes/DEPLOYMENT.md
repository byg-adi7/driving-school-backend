# Deployment

This app runs on [Render](https://render.com) as a Docker web service, with a Render
Postgres database and a Render Key Value (Redis-compatible) instance. Deploys are done by
Render itself through its GitHub integration - there is no deploy step in
`.github/workflows/ci.yml`. With the service's **Auto-Deploy** set to **After CI Checks
Pass** (step 5), every push to `main` deploys automatically, but only once the GitHub
Actions checks (`Build & test`, `Docker image builds`) have succeeded; if any fail, nothing
deploys and the current live version keeps running.

## One-time setup

### 1. Create the Postgres database and Key Value instance

Create both in the **same region** as the web service - internal connections (below) only
work between services in the same account and region.

- **Postgres** ("New" → "Postgres"). Its **Info** page (or the **Connect** menu) lists the
  hostname, port, database, username and password, plus assembled internal and external
  URLs. Use the **internal** hostname - Render's docs recommend internal connections
  wherever possible (private network, lower latency).
- **Key Value** ("New" → "Key Value"). Its internal URL looks like `redis://<host>:<port>`
  - take the host and port from it. Internal connections are unauthenticated by default,
  so `REDIS_PASSWORD` stays unset unless you enable authentication on the instance.
  Redis holds refresh-token revocations (logout, password reset), the rate-limit counters
  and the read caches. Set its **maxmemory policy** to `noeviction`: under an eviction
  policy such as `allkeys-lru` (Render's suggestion for pure caches), a full instance
  could silently evict a revocation entry and bring an already-revoked refresh token back
  to life.

> **Free-tier warnings (from Render's docs):** a free Render Postgres database **expires
> 30 days after creation** and supports **no backups**; a free Key Value instance **loses
> all its data on every restart** (so every logout/password-reset revocation is forgotten
> until those tokens expire on their own); and a free web service spins down after 15
> minutes without traffic, taking about a minute to wake on the next request. None of
> those are acceptable for real users - use paid instances for production.

### 2. Create the web service

"New" → "Web Service" → connect this GitHub repo, branch `main`, then:

| Setting | Value | Why |
|---|---|---|
| Runtime | Docker | The app ships its own multi-stage `Dockerfile`. |
| Root Directory | `project-with-changes` | Everything the build needs lives there. Render builds and runs relative to it, and only auto-deploys on changes under it. |
| Dockerfile Path | `./Dockerfile` | Relative to the root directory. |
| Health Check Path | `/actuator/health/readiness` | Public (see `SecurityConfig`), and only reports UP once the app can take traffic. |

### 3. Configure the web service's environment variables

In the web service's **Environment** tab, set:

| Variable | Value | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates `application-prod.yml` |
| `DB_HOST` | the Postgres **internal** hostname | From the database's Info page / Connect menu (step 1) |
| `DB_PORT` | `5432` | Render Postgres's port, as shown on the Info page |
| `DB_NAME` | the database name | Info page |
| `DB_USERNAME` | the username | Info page |
| `DB_PASSWORD` | the password | Info page |
| `REDIS_HOST` | the host from the Key Value **internal** URL | `redis://<host>:<port>` (step 1) |
| `REDIS_PORT` | the port from the same URL | |
| `REDIS_PASSWORD` | leave unset | Internal Key Value connections are unauthenticated by default; set it only if you enable auth on the instance |
| `JWT_SECRET` | a random string, 32+ characters | e.g. `openssl rand -base64 48` |
| `JWT_ACCESS_EXPIRATION_MS` / `JWT_REFRESH_EXPIRATION_MS` | leave unset | Token lifetimes: 900000 (15 minutes) and 604800000 (7 days) by default. |
| `BOOTSTRAP_ADMIN_ENABLED` | `true` for the very first deploy only | Flip back to `false` after you've logged in once and changed the password |
| `BOOTSTRAP_ADMIN_EMAIL` | your admin email | |
| `BOOTSTRAP_ADMIN_PASSWORD` | a strong password | Change it after first login regardless. **Do set this whenever `BOOTSTRAP_ADMIN_ENABLED=true`** - if left unset, the app now boots fine and simply skips creating the admin (logged as an error), rather than the startup crash this used to cause (an unresolvable placeholder was thrown as an exception from a `CommandLineRunner`, failing the whole app). |
| `CORS_ALLOWED_ORIGINS` | your real frontend origin(s), or `http://localhost:3000,http://localhost:5173` if the frontend isn't deployed yet | Comma-separated, no trailing slash. Defaults to the localhost dev origins if unset (deliberately - not a fake production-looking domain), so a locally-run frontend can still exercise the deployed backend before the frontend itself has anywhere to live. Update this value (Environment tab → save, which redeploys; no code change) once the frontend has a real URL. |
| `RESEND_API_KEY` | your [Resend](https://resend.com) API key | **Required for new accounts to log in:** login now needs a verified account, and the email verification code goes out through Resend - without it, sending a code fails with 503 and a new user can only verify by WhatsApp (if that's set up). Also needed for password-reset emails and the `EMAIL` notification channel. Sent over plain HTTPS via Resend's API, not raw SMTP (the app moved off SMTP when its previous host, Railway, blocked outbound SMTP). No fallback default - if unset, mail sending fails at send time (logged, not fatal). Free tier covers 3,000 emails/month. |
| `MAIL_FROM` | an address on a domain verified in Resend, or `onboarding@resend.dev` for testing | Must be a domain you've added and verified in Resend's dashboard for sending to arbitrary recipients - the sandbox address (`onboarding@resend.dev`) works without verification but can only send to your own account email. |
| `PASSWORD_RESET_URL` | your frontend's reset-password page URL, or `http://localhost:3000/reset-password` if not deployed yet | e.g. `https://yourapp.com/reset-password`. Defaults to the localhost dev URL if unset (same reasoning as `CORS_ALLOWED_ORIGINS`) - this is the base URL each password-reset email links to with `?token=...` appended, so it only matters once real users are actually requesting resets. Update it once the frontend has a real URL. |
| `PASSWORD_RESET_TOKEN_EXPIRATION_MS` | leave unset | How long a reset link works: 3600000 (1 hour) by default. |
| `STORAGE_PROVIDER` | `cloudinary` (default in prod), `gcs`, or `local` | **See the storage section below before going live.** |
| `CLOUDINARY_URL` | `cloudinary://<api_key>:<api_secret>@<cloud_name>` | Required when `STORAGE_PROVIDER=cloudinary`. Copy this directly from the Cloudinary dashboard ("API Environment variable") - it's the one variable Cloudinary's SDK needs, no separate cloud_name/key/secret fields. |
| `GCS_BUCKET_NAME` / `GCP_PROJECT_ID` | leave unset | Only when `STORAGE_PROVIDER=gcs` (the bucket is then required, the project optional). |
| `SENTRY_DSN` | your Sentry project's DSN | Optional - error tracking stays off (no-op) if unset. Get a DSN from [sentry.io](https://sentry.io) (or self-hosted Sentry). |
| `SENTRY_TRACES_SAMPLE_RATE` | a number 0.0-1.0, default `0.1` | Fraction of requests to trace for performance monitoring; only matters if `SENTRY_DSN` is set. |
| `OPENROUTE_API_KEY` | your [OpenRouteService](https://openrouteservice.org/dev/#/signup) API key | Powers practical-lesson route generation (`POST /api/v1/lesson-routes/generate`). No fallback default - if unset, route generation fails at request time with a clear error (logged, not fatal to the app) rather than silently calling the real API with a fake key. Free tier is generous enough for this app's scale; the directions endpoint URL and request timeout are fixed app config, not something you need to set. |
| `TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN` / `TWILIO_FROM_NUMBER` | your Twilio credentials and sending number | Optional. SMS (booking scheduled/cancelled texts) only sends once all three are set; until then the SMS channel is a safe no-op that records the message as FAILED. |
| `TWILIO_VERIFY_SERVICE_SID` | the `VA...` SID of a Twilio Verify service | Optional - turns on WhatsApp as an account-verification channel (email is always offered). Needs `TWILIO_ACCOUNT_SID`/`TWILIO_AUTH_TOKEN` too, but not `TWILIO_FROM_NUMBER`. Setup in the Twilio console: create a Verify service with **code length 6**, and connect your own WhatsApp sender (a WhatsApp Business number - Twilio requires your own sender for WhatsApp codes and creates the message templates itself). Only numbers stored in international format (`+233...`) are offered WhatsApp. |
| `VERIFICATION_REQUIRED` | `false` until real users can receive codes, then `true` (or unset) | Whether login requires a verified account. Defaults to `true`. Set `false` while email only goes to your own Resend account (no verified domain yet) so everyone can log in; accounts created meanwhile stay unverified and verify at their next login once it's `true` again. See "Going live with account verification" below. |
| `VERIFICATION_LOG_CODES` | leave unset | **Never set in production.** Local-development switch: when no `RESEND_API_KEY` is set, writes verification codes to the log instead of refusing to send them. On by default only in the `dev` profile. |
| `RATE_LIMIT_CLIENT_IP_HEADER` | leave unset | Which request header holds the visitor's real IP for rate limiting. Defaults to `CF-Connecting-IP` in the prod profile - verified on Render: it always carries the real IP and Cloudflare blocks requests that try to fake it, while `getRemoteAddr()` is a Cloudflare edge server and `X-Forwarded-For`'s first entry is client-controlled. Only change it if the app moves somewhere that isn't behind Cloudflare. |

`PORT` is injected by Render automatically (10000 by default) and is already wired up -
both `application.yml` and `application-prod.yml` read `${PORT:8080}`, so the app listens
on Render's port whatever profile is active. Don't set it yourself. (The base config used
to read `SERVER_PORT` instead, so without the `prod` profile the app listened on 8080
while Render expected `PORT`, and the deploy failed - fixed 2026-09-29.)

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
  across redeploys or restarts. Blocked outright when
  the `prod` profile is active (`StorageProperties` fails fast at startup) - it's a
  dev/test-only option, never a real choice for a live deployment.

### Render environment checklist

Every variable the app reads, and what to do with each on Render (web service →
**Environment**). Render never reads `.env` files or anything else from the repo for
these - secrets must not live in GitHub - so each one is typed in here, and saving
redeploys the service. The table above explains each variable in detail.

**Required - the app won't start (or can't work) without them**

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | from the Render Postgres Info page (internal hostname, port `5432`) |
| `REDIS_HOST` / `REDIS_PORT` | from the Render Key Value **internal** URL |
| `JWT_SECRET` | a random 32+ character string (`openssl rand -base64 48`) |
| `CLOUDINARY_URL` | from the Cloudinary dashboard (the prod default storage is Cloudinary; startup fails without it) |
| `BOOTSTRAP_ADMIN_EMAIL` | the bootstrap admin's email - keep it set permanently |

**Needed for features that are live today**

| Variable | Value |
|---|---|
| `RESEND_API_KEY` | your Resend API key (email codes, password resets, email notifications) |
| `MAIL_FROM` | `onboarding@resend.dev` for now; `Aidly <no-reply@mail.yourdomain.com>` once a domain is verified in Resend |
| `VERIFICATION_REQUIRED` | `false` for now (no verified email domain yet); `true` or delete it once `MAIL_FROM` is on your own domain |
| `OPENROUTE_API_KEY` | your OpenRouteService key (practical-lesson route generation) |
| `CORS_ALLOWED_ORIGINS` | the frontend's URL(s), comma-separated, once it's deployed (localhost dev origins until then) |
| `PASSWORD_RESET_URL` | the frontend's reset-password page once deployed (`http://localhost:3000/reset-password` until then) |

**Bootstrap admin (first deploy only)**

| Variable | Value |
|---|---|
| `BOOTSTRAP_ADMIN_ENABLED` | `true` for the first deploy, then `false` |
| `BOOTSTRAP_ADMIN_PASSWORD` | a strong password for the first deploy; can be deleted once `BOOTSTRAP_ADMIN_ENABLED=false` |

**Optional - switch features on when you're ready**

| Variable | Turns on |
|---|---|
| `TWILIO_ACCOUNT_SID` + `TWILIO_AUTH_TOKEN` + `TWILIO_FROM_NUMBER` | booking SMS texts |
| `TWILIO_ACCOUNT_SID` + `TWILIO_AUTH_TOKEN` + `TWILIO_VERIFY_SERVICE_SID` | WhatsApp verification codes |
| `SENTRY_DSN` (+ `SENTRY_TRACES_SAMPLE_RATE`, default `0.1`) | error tracking |

**Don't set** - Render or the defaults handle them: `PORT` (Render injects it),
`REDIS_PASSWORD` (internal Key Value has no auth), `RATE_LIMIT_CLIENT_IP_HEADER`
(`CF-Connecting-IP` in prod), `STORAGE_PROVIDER` (`cloudinary` in prod), the
`JWT_*_EXPIRATION_MS` / `PASSWORD_RESET_TOKEN_EXPIRATION_MS` lifetimes, `GCS_BUCKET_NAME` /
`GCP_PROJECT_ID` (only for `gcs` storage), and **never** `VERIFICATION_LOG_CODES` (it would
write login codes into the logs).

**Checking a change took effect:** the new values only apply after the redeploy that saving
triggers has gone live (Events tab). Some settings announce themselves at startup - e.g.
`VERIFICATION_REQUIRED=false` logs `Account verification is OFF`.

### 4. Create the bootstrap admin on the first deploy

With `BOOTSTRAP_ADMIN_ENABLED=true` (and `BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD`
set), the first successful boot creates the one bootstrap admin account. Log in, change the
password, then set `BOOTSTRAP_ADMIN_ENABLED=false` - after that `BOOTSTRAP_ADMIN_PASSWORD` is
no longer read and can be deleted. **Keep `BOOTSTRAP_ADMIN_EMAIL` set for good:** every start
looks the bootstrap admin up by that address (without it the prod default,
`admin@company.com`, is used and the real admin isn't found).

### 5. Turn on auto-deploy after CI

Web service → **Settings** → **Auto-Deploy** → **After CI Checks Pass**. From then on,
every push to `main` deploys automatically once GitHub Actions is green. Per Render's docs,
checks concluding `success`, `neutral` or `skipped` count as passed; if any check fails - or
a push has no checks at all - Render doesn't deploy it.

## Going live with account verification

Login requires a one-time code for every account created since verification shipped
(older accounts were marked verified). Codes go by email through Resend, or by WhatsApp
through Twilio Verify when that's configured.

**Until you own a domain** Resend only delivers from `onboarding@resend.dev` to your own
Resend account email, so other new users could never receive a code. Run with
`VERIFICATION_REQUIRED=false` meanwhile: everyone logs in with just their password.
To test the code screens, temporarily set it to `true` and use an account whose email
is your Resend account's.

**Once you have a domain:**

1. Resend dashboard → Domains → add a subdomain such as `mail.yourdomain.com`, copy the
   DNS records it shows into your registrar's DNS settings, and wait for "Verified".
2. On Render set `MAIL_FROM` to an address on it, e.g. `Aidly <no-reply@mail.yourdomain.com>`
   (keep `RESEND_API_KEY`).
3. Set `VERIFICATION_REQUIRED=true` (or delete the variable - `true` is the default). Render
   redeploys on save.
4. Log in with a new test account on a real inbox and confirm the code arrives.

Every account created while the switch was off then verifies at its next login.
WhatsApp can be switched on at any time independently (`TWILIO_VERIFY_SERVICE_SID`, see
the table above).

## How the pipeline works

`.github/workflows/ci.yml` runs on every push to `main` and every pull request into it:
`test` (the full Maven build: unit tests, the Testcontainers-backed integration tests,
JaCoCo and SpotBugs reports) and then `docker-build` (builds the production image to prove
the Dockerfile still works - the same Dockerfile Render builds from - without pushing it
anywhere). Render watches those checks on `main` and deploys only when they pass (step 5).
Pull requests get the same checks but never deploy - only what's merged into `main` does.

To deploy without a push (e.g. to pick up a changed environment variable), use **Manual
Deploy** on the service page, or the service's **Deploy Hook** URL (Settings), which
triggers a deploy on an HTTP GET or POST.

## Realtime (WebSocket) and instance count

The app pushes live updates to the frontend over a WebSocket at `/ws` (STOMP; see the
frontend API guide). Render supports WebSockets with no extra setting and no fixed
connection timeout, but drops every connection on each deploy or restart - clients
reconnect on their own.

**Keep the web service at one instance** unless this is changed: pushes go through
Spring's in-memory broker, so they only reach users connected to the same instance that
produced the event, and Render's load balancer assigns each WebSocket connection to a
random instance. Scaling out needs a shared relay between instances (e.g. Redis pub/sub
on the existing Key Value instance) - a code change, not a setting.

## Observability

- **Metrics**: `/actuator/prometheus` exposes Micrometer/Prometheus-format metrics in
  prod. It's still behind `SecurityConfig`'s `/actuator/** -> hasRole('ADMIN')` rule, so
  a real Prometheus server needs either network-level access to the app (e.g. both
  running on Render's private network) or a scrape credential with the ADMIN role -
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
in step 3 above) are stored as Render environment variables on the web service - never
committed to the repo, never in build logs.

**Rotation**: update the value in the service's **Environment** tab and save (which
redeploys). No code change needed for any secret in the table above. Rotating `JWT_SECRET`
invalidates every issued token, so every user has to log in again.

**If you outgrow this** - multiple environments needing centrally-managed/audited secrets,
automatic rotation, dynamic short-lived database credentials - the next step is HashiCorp
Vault or a cloud provider's secrets manager (AWS Secrets Manager, GCP Secret Manager).
That's deliberately not set up now: it requires running infrastructure this project doesn't
have yet, and would be premature complexity for a single-environment deployment.
Revisit if/when that changes.

## Backups

Render Postgres handles backups itself (not on the free tier, which has none - see step 1);
this project doesn't run its own backup tooling. What Render provides, per its docs:

- **Point-in-time recovery**: restore to any moment in the past 3 days on a Hobby
  workspace, or the past 7 days on Pro or higher.
- **Logical exports** on demand: database → **Recovery** → **Create export**, kept for
  seven days.

The concrete strategy for this project:

1. **Run production on a paid Postgres instance**, so recovery exists at all.
2. **Always create an export immediately before a risky migration** (see the
   migration-rollback runbook below) - point-in-time recovery covers the general case,
   but an explicit export is a known-good point you chose deliberately.
3. **To restore**: Render restores into a **new** database instance (with its own
   connection details), not over the live one. Check the recovered data, then update the
   web service's `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` (step 3) to the
   new instance, let it redeploy, and verify `/actuator/health/readiness` before considering
   the incident closed.
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
   On Render: database → **Recovery** → **Create export** (see Backups above).
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
