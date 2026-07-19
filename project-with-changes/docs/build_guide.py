# -*- coding: utf-8 -*-
import _docx_builder as d

def ENDPOINT(method, path, summary, access=None, params=None, request=None,
             example_req=None, response=None, example_resp=None, notes=None):
    d.RUNS((f"{method}  ", {"bold": True, "mono": True}), (path, {"mono": True}))
    d.P(summary, italic=True)
    if access:
        d.RUNS(("Access: ", {"bold": True}), (access, {}))
    if params:
        d.P("Query / path parameters:", bold=True)
        d.T(["Name", "Type", "Required", "Notes"], params)
    if request:
        d.P("Request body:", bold=True)
        d.T(["Field", "Type", "Required", "Notes"], request)
    if example_req:
        d.P("Example request:", bold=True)
        d.CODE(example_req)
    if response:
        d.P("Response (key fields):", bold=True)
        d.T(["Field", "Type", "Notes"], response)
    if example_resp:
        d.P("Example response:", bold=True)
        d.CODE(example_resp)
    if notes:
        d.P("Notes:", bold=True)
        d.BULLETS(notes)
    d.SPACER()

# =====================================================================
# COVER / INTRO
# =====================================================================
d.H(1, "Driving School Backend — Frontend API Guide")
d.P("This document is the reference a frontend developer needs to build a client "
    "against the driving school management backend. It covers every endpoint, "
    "authentication, conventions, and the things the frontend must implement "
    "itself that aren't just \"call this endpoint\".")
d.SPACER()

d.H(2, "Base URL")
d.CODE("https://<your-railway-domain>.up.railway.app/api/v1")
d.P("Every path in this document is relative to that base (e.g. \"POST /auth/login\" "
    "means POST https://.../api/v1/auth/login). Every endpoint requires a Bearer JWT "
    "in the Authorization header EXCEPT: login, refresh-token, forgot-password, "
    "reset-password. (Register also requires a token — see the Authentication section, "
    "there is no public self-registration.)")

# =====================================================================
# CONVENTIONS
# =====================================================================
d.H(1, "Conventions")

d.H(2, "Response envelope")
d.P("Every response — success or failure — is wrapped in the same shape:")
d.CODE('{\n  "success": true,\n  "message": "Operation successful",\n  "data": { ... }\n}')
d.P("On failure, success is false, data is omitted, and message explains what went wrong:")
d.CODE('{\n  "success": false,\n  "message": "Instructor and student must belong to the same school"\n}')
d.BULLETS([
    "Always check the success field, not just the HTTP status code, and surface message "
    "to the user on failure.",
    "A field that is null is omitted from the JSON entirely (not sent as null) — don't "
    "assume every documented field is always present in every response.",
])

d.H(2, "HTTP status codes used")
d.T(["Code", "Meaning in this API"], [
    ["200", "Success (GET, most PUT/PATCH/POST actions that don't create a resource)"],
    ["201", "Success, a new resource was created (POST that creates something)"],
    ["400", "Validation failure or a business-rule violation (e.g. conflicting booking, wrong workflow stage)"],
    ["401", "Missing/expired/invalid access token"],
    ["403", "Authenticated, but wrong role or not the owner of this resource"],
    ["404", "Resource doesn't exist"],
    ["500", "Unexpected server error — report these, they're bugs"],
])

d.H(2, "Pagination")
d.P("Endpoints that return a page of results accept standard Spring pagination query "
    "parameters and return a page envelope inside data:")
d.CODE("GET /lesson-questions/my-questions?page=0&size=20&sort=createdAt,desc")
d.CODE('{\n  "content": [ ... ],\n  "totalElements": 47,\n  "totalPages": 3,\n  "number": 0,\n  "size": 20,\n  "first": true,\n  "last": false\n}')
d.P("page is zero-indexed. sort can be repeated for multiple sort keys "
    "(e.g. &sort=status,asc&sort=createdAt,desc).")

d.H(2, "Dates and times")
d.P("All date-times are ISO-8601 local date-time, no timezone suffix "
    "(e.g. 2026-08-20T14:30:00) — the server has no concept of the caller's timezone, "
    "so the frontend is responsible for any timezone conversion/display logic.")

d.H(2, "Roles")
d.P("Three roles exist: ADMIN, INSTRUCTOR, STUDENT. Most endpoints layer an ownership "
    "check on top of the role check — e.g. an instructor can update their OWN profile "
    "and OWN courses, but not another instructor's. Where that matters, it's called out "
    "per-endpoint below as \"Access\".")

d.H(2, "Two kinds of ADMIN: bootstrap vs. school-owning")
d.P("Not all ADMIN accounts are equal. There is exactly one permanent \"bootstrap "
    "admin\" (created from server-side config on first deploy, not via the API) with "
    "unrestricted visibility over every school and every other admin — it owns no "
    "school of its own and can never be deleted, by anyone, through any endpoint. "
    "Every OTHER admin owns exactly one school, always: an admin account and its "
    "school are created together in one call and can only ever be deleted together. "
    "GET /auth/me's bootstrapAdmin field tells you which kind of admin you're "
    "building UI for — the two need meaningfully different admin dashboards (see the "
    "Schools section below).")

# =====================================================================
# AUTHENTICATION
# =====================================================================
d.H(1, "Authentication")
d.P("This is the first thing to implement — almost everything else needs a token.")

d.H(2, "There is no public self-registration")
d.P("POST /auth/register requires the caller to ALREADY be authenticated as ADMIN or "
    "INSTRUCTOR. An instructor can only create STUDENT accounts, and only within their "
    "own school. An admin can create either role, in any school. There is a separate "
    "POST /auth/admin/register that can create a STUDENT or INSTRUCTOR anywhere — but "
    "NOT an admin account anymore (see below).")
d.P("Practically: a brand-new deployment only has the one bootstrap admin account "
    "(created from server-side config, not via the API). Every other account — "
    "instructors and students — gets created by an admin or instructor logged into "
    "the app, e.g. from an admin \"add instructor\" screen or an instructor \"enroll "
    "student\" screen. There is no public sign-up form to build.")
d.P("A regular (school-owning) admin account can ONLY be created together with its "
    "school, via POST /schools (bootstrap-admin-only, see the Schools section) — "
    "there's no way to create a standalone admin account anymore. "
    "POST /auth/admin/register now rejects role=ADMIN outright with a 400 pointing "
    "you at POST /schools instead.")

ENDPOINT("POST", "/auth/login", "Authenticate and receive tokens.",
    access="Public (no token needed)",
    request=[
        ["email", "string", "yes", "valid email"],
        ["password", "string", "yes", "8-100 characters"],
    ],
    example_req='{\n  "email": "instructor.smith@example.com",\n  "password": "SecurePass123!"\n}',
    response=[
        ["accessToken", "string (JWT)", "short-lived (~15 min), send as Authorization: Bearer <token>"],
        ["refreshToken", "string (JWT)", "long-lived (~7 days), used only against /auth/refresh-token"],
        ["expiresIn", "number", "access token lifetime in seconds"],
        ["user.id / user.email / user.roles", "", "basic identity, roles is an array e.g. [\"INSTRUCTOR\"]"],
    ],
    example_resp='{\n  "success": true,\n  "message": "Login successful",\n  "data": {\n    "accessToken": "eyJhbGciOi...",\n    "refreshToken": "eyJhbGciOi...",\n    "tokenType": "Bearer",\n    "expiresIn": 900,\n    "user": { "id": 12, "email": "instructor.smith@example.com", "roles": ["INSTRUCTOR"] }\n  }\n}',
    notes=["Immediately after login, call GET /auth/me (below) — the login response does "
           "not include the instructor/student profile ID you'll need for most other calls."])

ENDPOINT("GET", "/auth/me", "Get the current user's identity and profile IDs.",
    access="Any authenticated user",
    response=[
        ["userId", "number", "the raw User.id — rarely needed directly"],
        ["email / roles", "", ""],
        ["studentProfileId", "number or null", "set only if the user has a STUDENT role"],
        ["instructorProfileId", "number or null", "set only if the user has an INSTRUCTOR role"],
        ["schoolId", "number or null", "the user's school — for an ADMIN this is the school they OWN "
         "(null for the bootstrap admin, who owns none)"],
        ["bootstrapAdmin", "boolean", "true only for the one permanent super-admin — decides which "
         "admin dashboard to render, see the Roles section above"],
        ["enabled / emailVerified", "boolean", ""],
    ],
    example_resp='{\n  "success": true,\n  "message": "Operation successful",\n  "data": {\n    "userId": 12,\n    "email": "instructor.smith@example.com",\n    "roles": ["INSTRUCTOR"],\n    "studentProfileId": null,\n    "instructorProfileId": 4,\n    "schoolId": 2,\n    "bootstrapAdmin": false,\n    "enabled": true,\n    "emailVerified": false\n  }\n}',
    notes=["This is critical: booking, lesson-note, lesson-route, and license-workflow "
           "endpoints all take a StudentProfile.id or InstructorProfile.id in their path or "
           "body — NOT the raw user ID. Cache instructorProfileId / studentProfileId from "
           "this call right after login.",
           "For an ADMIN caller, schoolId is their OWNED school (from POST /schools), not a "
           "profile-based school like students/instructors have — use it the same way either "
           "way (e.g. as the schoolId for GET /students/school/{schoolId})."])

ENDPOINT("POST", "/auth/register", "Create a STUDENT or INSTRUCTOR account.",
    access="ADMIN or INSTRUCTOR bearer token",
    request=[
        ["email", "string", "yes", ""],
        ["password", "string", "yes", "8-100 chars"],
        ["firstName / lastName", "string", "yes", ""],
        ["phone", "string", "no", ""],
        ["dateOfBirth", "date (YYYY-MM-DD)", "no", ""],
        ["schoolId", "number", "yes", "instructor callers can only use their own schoolId"],
        ["role", "enum", "yes", "STUDENT or INSTRUCTOR — instructor callers may only create STUDENT"],
        ["licenseNumber", "string", "yes for INSTRUCTOR, optional for STUDENT", "max 50 chars"],
        ["specialization", "string", "no", "instructor only"],
        ["yearsExperience", "number", "no", "instructor only"],
    ],
    example_req='{\n  "email": "student.jones@example.com",\n  "password": "SecurePass123!",\n  "firstName": "Jamie",\n  "lastName": "Jones",\n  "schoolId": 2,\n  "role": "STUDENT",\n  "licenseNumber": "LIC-2026-0031"\n}',
    response=[["Same shape as /auth/login's response", "", "the newly created user is also logged in / issued tokens for immediately, if that's the flow you want, or just discard the tokens and let them log in separately"]],
    notes=["An INSTRUCTOR caller creating a STUDENT in a DIFFERENT school than their own is rejected.",
           "licenseNumber is required when role=INSTRUCTOR (a common mistake — don't forget it on an \"add instructor\" form)."])

ENDPOINT("POST", "/auth/admin/register", "Admin creates a STUDENT or INSTRUCTOR in any school.",
    access="ADMIN only",
    request=[["Same fields as /auth/register", "", "", "role must be STUDENT or INSTRUCTOR"]],
    notes=["role=ADMIN is rejected with 400 — admin accounts can only be created via "
           "POST /schools now (they can't exist without a school to own). Use this endpoint "
           "for a cross-school \"admin creates a student/instructor anywhere\" screen — for "
           "normal instructor/student onboarding within one school, /auth/register is simpler."])

ENDPOINT("POST", "/auth/refresh-token", "Exchange a refresh token for a new access token.",
    access="Public (the refresh token itself is the credential)",
    request=[["refreshToken", "string", "yes", "the refreshToken from login"]],
    example_req='{\n  "refreshToken": "eyJhbGciOi..."\n}',
    response=[["Same shape as /auth/login's response", "", "a new access + refresh token pair"]],
    notes=["Standard pattern: on any 401 from a protected endpoint, call this once, retry the "
           "original request with the new access token, and if that ALSO 401s, force a full re-login."])

ENDPOINT("POST", "/auth/logout", "Revoke a refresh token server-side.",
    access="Any authenticated user (needs a valid access token to call this, revokes the refresh token in the body)",
    request=[["refreshToken", "string", "yes", ""]],
    notes=["Call this on explicit user logout so the refresh token can't be replayed later, "
           "then discard both tokens client-side."])

ENDPOINT("POST", "/auth/forgot-password", "Request a password-reset email.",
    access="Public",
    request=[["email", "string", "yes", ""]],
    notes=["Always returns the same generic success message regardless of whether the email "
           "exists, and regardless of whether the email actually sends — this is deliberate "
           "(don't leak account existence). Don't build UI that expects a different response for "
           "\"email not found\"."])

ENDPOINT("POST", "/auth/reset-password", "Complete a password reset using the emailed token.",
    access="Public",
    request=[
        ["token", "string", "yes", "from the reset-password link's ?token= query param"],
        ["newPassword", "string", "yes", "8-100 chars"],
    ])

ENDPOINT("DELETE", "/auth/me", "Delete (or request deletion of) the current user's own account.",
    access="Any authenticated user",
    notes=["Behavior depends on the caller's role — the response status tells you which "
           "happened, don't assume it's always an immediate delete:",
           "STUDENT / INSTRUCTOR: 200, immediate self soft-delete, unchanged from before.",
           "Regular (school-owning) ADMIN: 202 Accepted — this does NOT delete anything "
           "immediately. It creates a deletion request for their own school, which also "
           "requires the bootstrap admin's approval before the school and this account are "
           "actually removed together. Same response shape as DELETE /schools/me below — in "
           "fact it just triggers that same flow. Show the admin a \"pending approval\" state, "
           "not a normal \"account deleted\" confirmation.",
           "Bootstrap admin: 400, always — this account can never be deleted, by anyone, "
           "through any endpoint. Don't show a delete-account option in the bootstrap "
           "admin's own settings UI at all.",
           "There is a separate ADMIN-only endpoint (DELETE /users/{id}) to delete a "
           "DIFFERENT user's account."])

# =====================================================================
# SCHOOLS
# =====================================================================
d.H(1, "Schools")
d.P("A school is the top-level tenant — every user, course, vehicle, and booking "
    "belongs to exactly one school. Cross-school actions (an instructor booking a "
    "student from a different school, for example) are rejected.")
d.P("A school cannot exist without its own admin, and that admin cannot exist without "
    "the school — the two are created together in one call, and can only ever be "
    "deleted together. Only the bootstrap admin can create a school (and its admin) or "
    "delete one directly; a regular admin can only request deletion of their OWN "
    "school, gated on the bootstrap admin's approval. See \"Two kinds of ADMIN\" in the "
    "Roles section above before building any of this.")

ENDPOINT("POST", "/schools", "Create a new school and its owning admin account together.",
    access="Bootstrap admin only",
    request=[
        ["schoolName", "string", "yes", "max 200 chars"],
        ["schoolAddress", "string", "yes", "max 500 chars"],
        ["schoolPhone", "string", "no", "max 20 chars"],
        ["schoolEmail", "string", "no", "valid email, max 255 chars"],
        ["adminEmail", "string", "yes", "valid email — becomes the new admin's login"],
        ["adminPassword", "string", "yes", "8-100 chars"],
    ],
    example_req='{\n  "schoolName": "Downtown Driving Academy",\n  "schoolAddress": "42 Main Street, Springfield",\n  "schoolPhone": "555-0142",\n  "schoolEmail": "info@downtowndriving.example",\n  "adminEmail": "owner@downtowndriving.example",\n  "adminPassword": "SecurePass123!"\n}',
    response=[
        ["school", "object", "same shape as the GET /schools/{id} response below"],
        ["adminUserId", "number", "the new admin's raw User.id"],
        ["adminEmail", "string", "echoes the email you just set — log this admin in separately with adminPassword"],
    ],
    notes=["A non-bootstrap admin calling this gets 400 — there is no self-service "
           "\"create your own school\" flow.",
           "There is no adminFirstName/adminLastName — an admin account has no profile "
           "record of its own (unlike students/instructors), just an email/password login. "
           "Don't build name fields into this form.",
           "The response's school field is the ONLY school the newly created admin will "
           "ever see or manage — hand the adminEmail/adminPassword to whoever should log in "
           "as that school's admin, they aren't emailed automatically."])

ENDPOINT("GET", "/schools/{id}", "Get a school by ID.",
    access="Bootstrap admin (any school), or a regular admin viewing their OWN school",
    notes=["A regular admin requesting a DIFFERENT school's ID gets 400, not the data — "
           "always use the caller's own schoolId (from GET /auth/me) unless you know "
           "you're bootstrap.",
           "Every path under /schools/** other than the exact GET /schools list endpoint "
           "below is restricted to ADMIN at the security-filter level — instructors/"
           "students get 403. GET /auth/me already returns their own schoolId/schoolName, "
           "there's no need to call this for them."])

ENDPOINT("GET", "/schools", "List schools.",
    access="Any authenticated user",
    notes=["Bootstrap admin: every active school. Regular admin: a single-item list "
           "containing only their own school (or empty, which shouldn't normally "
           "happen). Instructor/student: the full active-schools list, unchanged — this "
           "is the registration-picker use case, unaffected by the admin-ownership model."])

ENDPOINT("DELETE", "/schools/{id}", "Permanently delete a school and its owning admin.",
    access="Bootstrap admin only",
    notes=["Immediate, no confirmation step — deletes the school AND its admin's account "
           "together, plus every student, instructor, booking, course, quiz, and everything "
           "else tied to that school. There is no undo. Build a real \"are you sure\" "
           "confirmation dialog client-side; the API itself doesn't have a two-step version "
           "of this call (that's what the request/approve flow below is for non-bootstrap "
           "admins).",
           "If there happened to be a pending deletion request for this school (see below), "
           "it's automatically marked approved — you don't need to resolve that separately."])

ENDPOINT("DELETE", "/schools/me", "Admin: request deletion of your own school.",
    access="Regular (non-bootstrap) admin only",
    notes=["Returns 202 Accepted, not 200 — nothing is deleted yet. This only creates a "
           "pending request; the bootstrap admin must approve it before the school and "
           "your own account are actually removed (together, per the create-together/"
           "delete-together rule above).",
           "400 if you already have a request pending — check the response status before "
           "letting the admin submit a second one.",
           "The bootstrap admin is notified automatically (email + in-app) — there's "
           "nothing else for the frontend to trigger here.",
           "Response shape is the same SchoolDeletionRequestResponse used by the bootstrap "
           "review-queue endpoints below (status, schoolName, requestedByEmail, etc.) — "
           "show the admin a \"request submitted, awaiting approval\" screen using it."])

# =====================================================================
# SCHOOL DELETION REQUESTS (bootstrap admin review queue)
# =====================================================================
d.H(1, "School Deletion Requests")
d.P("The bootstrap admin's review queue for the DELETE /schools/me requests above. "
    "Build this as a dedicated admin screen only the bootstrap admin ever sees — a "
    "regular admin has no use for it and gets 403 on all three endpoints.")

ENDPOINT("GET", "/school-deletion-requests", "List pending deletion requests.",
    access="Bootstrap admin only",
    params=[["page / size / sort", "standard pagination", "no", "see the Pagination convention above"]],
    response=[["Paginated — each row:", "", ""],
              ["id / schoolName / requestedByEmail / status / createdAt", "", "status is always PENDING in this list"]],
    notes=["This is the badge-count / inbox screen for the bootstrap admin — poll it or "
           "refresh after handling the in-app notification that a new request arrived."])

ENDPOINT("POST", "/school-deletion-requests/{id}/approve", "Approve a request — permanently deletes the school and admin.",
    access="Bootstrap admin only",
    request=[["reviewNotes", "string", "no", "max 1000 chars, optional audit note"]],
    notes=["This is the point of no return for that school — same irreversible cascade as "
           "DELETE /schools/{id} direct-delete, just gated behind this approval step "
           "instead. Build a real confirmation dialog here too.",
           "400 if the request was already approved/rejected by someone else in the "
           "meantime — refresh the list on that error rather than retrying blindly."])

ENDPOINT("POST", "/school-deletion-requests/{id}/reject", "Reject a request — school and admin are untouched.",
    access="Bootstrap admin only",
    request=[["reviewNotes", "string", "no", "max 1000 chars — worth making this required in your UI even though the API allows omitting it, so the admin knows why"]],
    notes=["Nothing is deleted. The requesting admin keeps their school and account "
           "exactly as before; they can submit a new request later if they still want to."])

# =====================================================================
# ROLES
# =====================================================================
d.H(1, "Roles")
ENDPOINT("GET", "/roles", "List the three system roles.", access="ADMIN or INSTRUCTOR",
    example_resp='{\n  "success": true,\n  "message": "Operation successful",\n  "data": [\n    {"id": 1, "name": "ADMIN", "description": "Administrator with full system access"},\n    {"id": 2, "name": "INSTRUCTOR", "description": "Driving instructor"},\n    {"id": 3, "name": "STUDENT", "description": "Student learner"}\n  ]\n}',
    notes=["This is static reference data — fetch once and cache, it never changes at runtime."])

# =====================================================================
# USERS
# =====================================================================
d.H(1, "Users (admin account management)")
ENDPOINT("DELETE", "/users/{id}", "Delete a user account.", access="ADMIN only",
    notes=["Behavior depends on what the target account is — check the response status, "
           "don't assume it's always the same kind of delete:",
           "Target is a STUDENT or INSTRUCTOR: immediate soft-delete, unchanged from before "
           "— any admin can do this.",
           "Target is a regular (school-owning) admin: only the BOOTSTRAP admin may call "
           "this (403 for any other admin, even against their own peers); it immediately "
           "cascade-deletes that admin's account AND their whole school together — same "
           "irreversible scope as DELETE /schools/{id}. This is an alternate entry point "
           "into the exact same deletion, not a lighter-weight one — build the same "
           "confirmation-dialog treatment for it.",
           "Target is the bootstrap admin: always 400, regardless of caller — that account "
           "can never be deleted through this endpoint (or any other)."])

# =====================================================================
# INSTRUCTOR PROFILES
# =====================================================================
d.H(1, "Instructor Profiles")
ENDPOINT("GET", "/instructors/me", "Get the current instructor's own profile.", access="INSTRUCTOR",
    response=[
        ["id", "number", "InstructorProfile.id — use this in booking/lesson-note/route calls"],
        ["userId / email / firstName / lastName / phone", "", ""],
        ["specialization / licenseNumber / yearsExperience / bio", "", ""],
        ["active", "boolean", "an inactive instructor can't be assigned new bookings"],
        ["schoolId / schoolName", "", ""],
    ])

ENDPOINT("PUT", "/instructors/me", "Update the current instructor's own profile.", access="INSTRUCTOR",
    request=[
        ["firstName / lastName", "string", "yes", ""],
        ["phone", "string", "no", "max 20 chars"],
        ["specialization", "string", "no", "max 200 chars, e.g. \"Manual transmission\""],
        ["yearsExperience", "number", "no", "≥ 0"],
        ["bio", "string", "no", ""],
    ])

ENDPOINT("GET", "/instructors/school/{schoolId}", "Admin: list instructors in a school.", access="ADMIN only")

ENDPOINT("PATCH", "/instructors/{id}/active", "Admin: activate or deactivate an instructor.", access="ADMIN only",
    request=[["active", "boolean", "yes", ""]])

# =====================================================================
# STUDENT PROFILES
# =====================================================================
d.H(1, "Student Profiles")
ENDPOINT("GET", "/students/me", "Get the current student's own profile.", access="STUDENT",
    response=[
        ["id", "number", "StudentProfile.id — use this in booking/quiz/lesson-note calls"],
        ["userId / email / firstName / lastName / phone", "", ""],
        ["dateOfBirth / enrollmentDate", "date", ""],
        ["status", "enum", "ACTIVE, INACTIVE, SUSPENDED, GRADUATED"],
        ["profileImageUrl / schoolId / schoolName", "", ""],
    ])

ENDPOINT("PUT", "/students/me", "Update the current student's own profile.", access="STUDENT",
    request=[
        ["firstName / lastName", "string", "yes", ""],
        ["phone", "string", "no", ""],
        ["dateOfBirth", "date", "no", "must be in the past"],
        ["profileImageUrl", "string", "no", "max 500 chars"],
    ])

ENDPOINT("GET", "/students/school/{schoolId}", "List students in a school.",
    access="ADMIN (any school), or an INSTRUCTOR listing their OWN school only",
    notes=["An INSTRUCTOR passing a DIFFERENT school's ID than their own (from GET /auth/me's "
           "schoolId) gets 400, not the data.",
           "This is the endpoint for a student picker on the instructor's \"book a lesson\" "
           "and \"add lesson note\" forms — use it instead of asking the instructor to type a "
           "raw numeric student ID."])

ENDPOINT("PATCH", "/students/{id}/status", "Admin: change a student's status.", access="ADMIN only",
    request=[["status", "enum", "yes", "ACTIVE, INACTIVE, SUSPENDED, GRADUATED"]])

# =====================================================================
# VEHICLES
# =====================================================================
d.H(1, "Vehicles")
ENDPOINT("POST", "/vehicles", "Add a vehicle to a school's fleet.", access="ADMIN only",
    request=[
        ["registrationNumber", "string", "yes", "max 20 chars, e.g. license plate"],
        ["make / model", "string", "yes", "max 50 chars each"],
        ["modelYear", "number", "yes", "1980-2100"],
        ["color", "string", "yes", "max 30 chars"],
        ["gpsDeviceId", "string", "no", "max 100 chars"],
        ["schoolId", "number", "yes", ""],
    ],
    example_req='{\n  "registrationNumber": "SPR-2026",\n  "make": "Toyota",\n  "model": "Corolla",\n  "modelYear": 2024,\n  "color": "Silver",\n  "schoolId": 2\n}')

ENDPOINT("GET", "/vehicles/{id}", "Get a vehicle by ID.", access="Any authenticated user")
ENDPOINT("GET", "/vehicles/school/{schoolId}", "List a school's vehicles.", access="Any authenticated user")
ENDPOINT("PUT", "/vehicles/{id}", "Update vehicle details.", access="ADMIN only",
    request=[["make / model / modelYear / color", "", "yes", "same constraints as create"],
             ["gpsDeviceId", "string", "no", ""]])
ENDPOINT("PATCH", "/vehicles/{id}/status", "Update a vehicle's status.", access="ADMIN only",
    request=[["status", "enum", "yes", "AVAILABLE, IN_USE, MAINTENANCE, OUT_OF_SERVICE"]],
    notes=["Only AVAILABLE vehicles can be assigned to a new booking."])

# =====================================================================
# COURSES / VIDEO LESSONS / RESOURCES
# =====================================================================
d.H(1, "Courses, Video Lessons & Resources")
d.P("A Course contains ordered Video Lessons; each Video Lesson can have attached "
    "Resources (PDFs, links, etc. — metadata only, see the note below). Courses and "
    "lessons each have a draft/published lifecycle: students only ever see published "
    "content; the owning instructor (or an admin) can see and edit their own drafts too.")

d.H(2, "Courses")
ENDPOINT("POST", "/courses", "Create a course (starts as DRAFT).", access="ADMIN or INSTRUCTOR",
    request=[
        ["title", "string", "yes", "max 200 chars"],
        ["description", "string", "no", "max 2000 chars"],
        ["instructorId", "number", "only for ADMIN callers", "an instructor caller always creates it under their own profile regardless of this field"],
    ],
    example_req='{\n  "title": "Highway Code Essentials",\n  "description": "Covers UK road signs, speed limits, and right-of-way rules."\n}')

ENDPOINT("PUT", "/courses/{courseId}", "Update a course's title/description.", access="ADMIN or the owning INSTRUCTOR",
    request=[["title / description", "string", "no (partial update)", "same limits as create"]])

ENDPOINT("PUT", "/courses/{courseId}/publish", "Publish a course.", access="ADMIN or the owning INSTRUCTOR")
ENDPOINT("PUT", "/courses/{courseId}/unpublish", "Unpublish a course back to draft.", access="ADMIN or the owning INSTRUCTOR")
ENDPOINT("PUT", "/courses/{courseId}/archive", "Archive a course.", access="ADMIN or the owning INSTRUCTOR")

ENDPOINT("GET", "/courses/{courseId}", "Get a course by ID.", access="ADMIN, INSTRUCTOR, or STUDENT",
    notes=["A DRAFT course is only visible to its owning instructor or an admin — a student "
           "(or a different instructor) gets 400 Bad Request, not 404, if they try to view "
           "someone else's draft."])

ENDPOINT("GET", "/courses", "List all published courses.", access="ADMIN, INSTRUCTOR, or STUDENT",
    notes=["This is the main course catalog / browse screen — identical result for every "
           "caller, drafts never appear here."])

ENDPOINT("GET", "/courses/mine", "List the current instructor's own courses, including drafts.", access="INSTRUCTOR")

d.H(2, "Video Lessons")
ENDPOINT("POST", "/video-lessons", "Add a video lesson to a course (starts unpublished).", access="ADMIN or the course's owning INSTRUCTOR",
    request=[
        ["courseId", "number", "yes", ""],
        ["title", "string", "yes", "max 200 chars"],
        ["description", "string", "no", "max 2000 chars"],
        ["videoUrl", "string", "yes", "max 500 chars — host the actual video file yourself (e.g. YouTube unlisted, Cloudinary, etc.); this API only stores the URL"],
        ["lessonOrder", "number", "yes", "≥ 1, controls display order within the course"],
        ["durationSeconds", "number", "no", "≥ 0"],
    ])
ENDPOINT("PUT", "/video-lessons/{lessonId}", "Update a video lesson.", access="ADMIN or owning INSTRUCTOR",
    request=[["title / description / videoUrl / lessonOrder / durationSeconds", "", "no (partial update)", "same limits as create"]])
ENDPOINT("PUT", "/video-lessons/{lessonId}/publish", "Publish a video lesson.", access="ADMIN or owning INSTRUCTOR")
ENDPOINT("PUT", "/video-lessons/{lessonId}/unpublish", "Unpublish a video lesson.", access="ADMIN or owning INSTRUCTOR")
ENDPOINT("GET", "/video-lessons/{lessonId}", "Get a video lesson by ID.", access="ADMIN, INSTRUCTOR, or STUDENT")
ENDPOINT("GET", "/video-lessons/course/{courseId}", "List lessons for a course.", access="ADMIN, INSTRUCTOR, or STUDENT",
    notes=["A student (or non-owning instructor) only sees published lessons in this list; "
           "the owning instructor/admin sees drafts too."])

d.H(2, "Resources")
ENDPOINT("POST", "/resources", "Attach a resource to a video lesson.", access="ADMIN or the lesson's owning INSTRUCTOR",
    request=[
        ["lessonId", "number", "yes", ""],
        ["title", "string", "yes", "max 200 chars"],
        ["fileUrl", "string", "yes", "max 500 chars — again, host the file yourself and pass the URL"],
        ["type", "enum", "yes", "PDF, DOCUMENT, LINK, IMAGE, OTHER"],
    ])
ENDPOINT("DELETE", "/resources/{resourceId}", "Delete a resource.", access="ADMIN or owning INSTRUCTOR")
ENDPOINT("GET", "/resources/lesson/{lessonId}", "List resources for a lesson.", access="ADMIN, INSTRUCTOR, or STUDENT",
    notes=["Unlike lesson-note attachments (below), these are just metadata rows pointing at "
           "a URL you host — there's no upload/download endpoint here, the frontend fetches "
           "fileUrl directly."])

# =====================================================================
# QUIZZES
# =====================================================================
d.H(1, "Quizzes")
ENDPOINT("POST", "/quizzes", "Create a quiz for a course (starts unpublished).", access="ADMIN or the course's owning INSTRUCTOR",
    request=[
        ["courseId", "number", "yes", ""],
        ["title", "string", "yes", "max 200 chars"],
        ["description", "string", "no", "max 2000 chars"],
        ["passingScore", "number", "yes", "0-100 (percent)"],
        ["timeLimitMinutes", "number", "no", "≥ 1"],
        ["maxAttempts", "number", "yes", "≥ 1"],
    ],
    example_req='{\n  "courseId": 7,\n  "title": "Road Signs Final Test",\n  "passingScore": 70,\n  "maxAttempts": 2\n}')

ENDPOINT("POST", "/quizzes/{quizId}/questions", "Add a question to a quiz.", access="ADMIN or owning INSTRUCTOR",
    request=[
        ["questionText", "string", "yes", ""],
        ["questionType", "enum", "yes", "MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER"],
        ["options", "string", "no", "for MULTIPLE_CHOICE — free-form string, e.g. a JSON-encoded array you define and parse yourself client-side"],
        ["correctAnswer", "string", "yes", ""],
        ["points", "number", "yes", "≥ 1"],
        ["questionOrder", "number", "yes", "≥ 1"],
    ],
    notes=["Rejected with 400 once the quiz is already published — finish authoring questions "
           "before publishing."])

ENDPOINT("PUT", "/quizzes/{quizId}/publish", "Publish a quiz.", access="ADMIN or owning INSTRUCTOR",
    notes=["Rejected with 400 if the quiz has zero questions."])

ENDPOINT("GET", "/quizzes/{quizId}", "Get a quiz by ID.", access="ADMIN, INSTRUCTOR, or STUDENT",
    params=[["forStudent", "boolean, default true", "no", "when true, each question's correctAnswer is stripped from the response"]],
    notes=["IMPORTANT: this flag is not itself role-enforced — it's the caller's "
           "responsibility to always pass forStudent=true (or omit it, since that's the "
           "default) in any student-facing quiz-taking screen, and forStudent=false only in "
           "an instructor/admin authoring screen. Getting this backwards leaks answers to "
           "students."])

ENDPOINT("GET", "/quizzes/course/{courseId}", "List published quizzes for a course.", access="ADMIN, INSTRUCTOR, or STUDENT",
    notes=["Always returns answers stripped, regardless of caller — use the single-quiz "
           "endpoint above with forStudent=false for an authoring view of one quiz's answers."])

ENDPOINT("POST", "/quizzes/{quizId}/submit", "Submit answers for grading.", access="ADMIN or STUDENT",
    request=[
        ["studentId", "number", "yes", "ignored/overridden for STUDENT callers — they always submit as themselves; ADMIN may submit on behalf of any student (e.g. a manual paper-test entry)"],
        ["answers", "object", "yes", "map of questionId (as a string key) to the answer text"],
    ],
    example_req='{\n  "studentId": 15,\n  "answers": {\n    "101": "Stop",\n    "102": "Yield"\n  }\n}',
    response=[
        ["score", "number", "0-100"],
        ["passed", "boolean", ""],
        ["attemptNumber", "number", ""],
        ["status", "enum", "GRADED (quizzes are graded synchronously and immediately)"],
    ],
    notes=["Rejected with 400 once maxAttempts is exceeded.",
           "If this quiz is linked to the student's license workflow (see License Workflow "
           "section) and the attempt passes, the workflow automatically advances — no "
           "separate call needed from the frontend for that.",
           "The student is automatically sent an IN_APP notification with the result (pass "
           "or fail) after every submission — see the Notifications section.",
           "A PASSING attempt also awards gamification points (once per quiz — retaking an "
           "already-passed quiz does not award points again) — see the Gamification section."])

# =====================================================================
# BOOKINGS
# =====================================================================
d.H(1, "Bookings (practical lessons)")
d.P("Bookings are INSTRUCTOR-INITIATED ONLY. There is no student-facing \"book a lesson\" "
    "self-service flow — an instructor picks one of their own students, a time, and "
    "(optionally) a vehicle. This is a deliberate business rule, not a gap: build the "
    "\"schedule a lesson\" screen for the instructor role, not the student role.")

ENDPOINT("POST", "/bookings", "Create a booking.",
    access="ADMIN, or an INSTRUCTOR creating under their OWN instructorId",
    request=[
        ["studentId", "number", "yes", "StudentProfile.id"],
        ["instructorId", "number", "yes", "InstructorProfile.id — must match the caller's own profile if the caller is an INSTRUCTOR"],
        ["vehicleId", "number", "no", "omit if no vehicle needed for this lesson type"],
        ["scheduledAt", "datetime", "yes", "must be in the future"],
        ["durationMinutes", "number", "yes", "positive"],
        ["bookingType", "enum", "yes", "ROAD_LESSON, THEORY_SESSION, DRIVING_ASSESSMENT, PRACTICE_TEST"],
        ["notes", "string", "no", "max 1000 chars"],
    ],
    example_req='{\n  "studentId": 15,\n  "instructorId": 4,\n  "vehicleId": 3,\n  "scheduledAt": "2026-08-20T14:30:00",\n  "durationMinutes": 60,\n  "bookingType": "ROAD_LESSON",\n  "notes": "Focus on parallel parking"\n}',
    response=[
        ["id / status", "", "status starts as PENDING"],
        ["studentId / studentName / instructorId / instructorName / vehicleId / schoolId", "", ""],
        ["scheduledAt / endAt / durationMinutes", "", "endAt is computed server-side from scheduledAt + durationMinutes"],
        ["bookingType / notes", "", ""],
    ],
    notes=["Rejected with 400 if the instructor and student aren't in the same school, if "
           "the instructor is inactive, or if there's a conflicting booking for the student, "
           "instructor, or vehicle in that time slot.",
           "There is no pickupLocation field — pickup is always the driving school itself; "
           "don't build a pickup-address input for this.",
           "The student is automatically sent an IN_APP notification when a booking is "
           "created — see the Notifications section for how to surface that in the UI."])

ENDPOINT("GET", "/bookings/{id}", "Get a booking by ID.",
    access="ADMIN, or a participant (the assigned student or instructor)")
ENDPOINT("PUT", "/bookings/{id}/confirm", "Confirm a pending booking.",
    access="ADMIN, or the assigned INSTRUCTOR",
    notes=["Only valid from PENDING status."])
ENDPOINT("PUT", "/bookings/{id}/cancel", "Cancel a booking.",
    access="ADMIN, or a participant",
    notes=["Not valid once already COMPLETED or CANCELLED.",
           "The student is automatically sent an IN_APP notification that the lesson was "
           "cancelled — see the Notifications section."])
ENDPOINT("PUT", "/bookings/{id}/complete", "Mark a booking completed.",
    access="ADMIN, or the assigned INSTRUCTOR",
    notes=["Only valid from CONFIRMED status.",
           "This is also the trigger for the student's gamification points/streak — see the "
           "Gamification section. No separate call is needed from the frontend for that."])
ENDPOINT("GET", "/bookings/student/{studentId}", "List a student's bookings.",
    access="ADMIN, the student themselves, or an instructor who has taught that student")
ENDPOINT("GET", "/bookings/instructor/{instructorId}", "List an instructor's schedule in a date range.",
    access="ADMIN, or the instructor themselves",
    params=[["from", "datetime", "yes", "ISO-8601, e.g. 2026-08-01T00:00:00"],
            ["to", "datetime", "yes", ""]],
    notes=["Use this to render a calendar/schedule view — both from and to are required, "
           "there's no \"list everything\" mode."])

# =====================================================================
# LESSON ROUTES
# =====================================================================
d.H(1, "Practical Lesson Routes")
d.P("A route is a planned driving path for a specific booking, generated via an external "
    "routing service (the backend calls OpenRouteService server-side — the frontend never "
    "needs a maps API key of its own for this).")

ENDPOINT("POST", "/lesson-routes/generate", "Generate a route for a booking.", access="INSTRUCTOR only",
    request=[
        ["bookingId", "number", "yes", "must be one of the CALLING instructor's own bookings"],
        ["startLocation / destinationLocation", "string", "yes", "2-500 chars, human-readable labels"],
        ["startLatitude / startLongitude", "number", "yes", ""],
        ["destinationLatitude / destinationLongitude", "number", "yes", ""],
    ],
    example_req='{\n  "bookingId": 88,\n  "startLocation": "Downtown Driving Academy",\n  "destinationLocation": "Riverside Loop",\n  "startLatitude": 51.5074,\n  "startLongitude": -0.1278,\n  "destinationLatitude": 51.5155,\n  "destinationLongitude": -0.1410\n}',
    response=[
        ["distanceKm / durationMinutes", "number", "computed by the routing service"],
        ["coordinates", "array of {latitude, longitude}", "the actual path, for drawing a polyline on a map"],
    ],
    notes=["This depends on a third-party routing API — it can fail with 400 (\"Failed to "
           "generate route\") if that service is unreachable or the coordinates are unroutable; "
           "handle this as a retryable error in the UI, not a hard failure.",
           "You'll need a map-rendering library (e.g. Leaflet, Mapbox GL, Google Maps JS) "
           "client-side to actually draw the coordinates array — the API only returns "
           "raw lat/lng points."])

ENDPOINT("GET", "/lesson-routes/{id}", "Get a route by ID.", access="ADMIN, INSTRUCTOR, or STUDENT (participant of the underlying booking)")
ENDPOINT("GET", "/lesson-routes/booking/{bookingId}", "Get the route for a specific booking.", access="Same as above")
ENDPOINT("GET", "/lesson-routes/instructor/{instructorId}", "Paginated list of an instructor's routes.", access="INSTRUCTOR or ADMIN")
ENDPOINT("GET", "/lesson-routes", "Paginated list of ALL routes.", access="ADMIN only")
ENDPOINT("DELETE", "/lesson-routes/{id}", "Delete a route.", access="INSTRUCTOR only (their own)")

# =====================================================================
# LESSON NOTES + ATTACHMENTS
# =====================================================================
d.H(1, "Lesson Notes & Attachments")
d.P("An instructor writes a structured note after a lesson (summary / strengths / "
    "weaknesses / recommendations), optionally linked to the specific booking it's about, "
    "and can attach PDF files (real file uploads, stored on Cloudinary server-side).")

ENDPOINT("POST", "/lesson-notes", "Create a lesson note.", access="INSTRUCTOR only",
    request=[
        ["bookingId", "number", "no", "if provided, must actually belong to this instructor+student pair"],
        ["studentId", "number", "yes", "StudentProfile.id"],
        ["lessonSummary", "string", "yes", "10-1000 chars"],
        ["strengths", "string", "yes", "10-1500 chars"],
        ["weaknesses", "string", "yes", "10-1500 chars"],
        ["recommendations", "string", "yes", "10-1500 chars"],
    ],
    example_req='{\n  "bookingId": 88,\n  "studentId": 15,\n  "lessonSummary": "Covered roundabout navigation and lane discipline on the ring road.",\n  "strengths": "Confident mirror checks and consistent speed control.",\n  "weaknesses": "Hesitates when merging into fast-moving traffic.",\n  "recommendations": "Practice motorway-speed merging next session."\n}',
    notes=["All four text fields have a 10-character MINIMUM — a short placeholder like "
           "\"Good.\" will fail validation with 400.",
           "The student is automatically sent an IN_APP notification that a new note was "
           "added — see the Notifications section."])

ENDPOINT("PUT", "/lesson-notes/{id}", "Update a lesson note.", access="INSTRUCTOR only (their own)",
    request=[["lessonSummary / strengths / weaknesses / recommendations", "string", "no (partial update)", "same length limits as create"]])
ENDPOINT("GET", "/lesson-notes/{id}", "Get a lesson note by ID.", access="ADMIN, the owning INSTRUCTOR, or the note's STUDENT")
ENDPOINT("GET", "/lesson-notes/student/{studentId}", "Paginated notes for a student.", access="ADMIN, the student, or their instructor")
ENDPOINT("GET", "/lesson-notes/instructor/{instructorId}", "Paginated notes by an instructor.", access="ADMIN or INSTRUCTOR")
ENDPOINT("GET", "/lesson-notes", "Paginated list of ALL notes.", access="ADMIN only")
ENDPOINT("DELETE", "/lesson-notes/{id}", "Delete a lesson note.", access="INSTRUCTOR only (their own)")

d.H(2, "Attachments")
ENDPOINT("POST", "/lesson-notes/{lessonNoteId}/attachments", "Upload a PDF attachment.", access="ADMIN or INSTRUCTOR",
    request=[
        ["file", "file (multipart/form-data)", "yes", "PDF only, 50 MB max"],
        ["description", "string", "no", ""],
    ],
    notes=["This is the ONE endpoint in the whole API that needs multipart/form-data instead "
           "of JSON. Example using fetch/FormData:"])
d.CODE('const form = new FormData();\nform.append("file", pdfFile);\nform.append("description", "Signed parent consent form");\n\nawait fetch(`${BASE_URL}/lesson-notes/${noteId}/attachments`, {\n  method: "POST",\n  headers: { "Authorization": `Bearer ${accessToken}` }, // do NOT set Content-Type yourself\n  body: form,\n});')
d.P("(Let the browser set the multipart Content-Type header itself, including the "
    "boundary — setting it manually is a common bug.)")
d.SPACER()

ENDPOINT("GET", "/lesson-notes/{lessonNoteId}/attachments", "List active attachments.", access="Any authenticated user with access to the note",
    response=[
        ["id / fileName / fileType / fileSize / fileSizeFormatted", "", "fileSizeFormatted is a ready-to-display string e.g. \"2.53 MB\""],
        ["uploadedById / uploadedByName / createdAt", "", ""],
        ["downloadCount", "number", ""],
        ["isActive", "boolean", ""],
        ["downloadUrl", "string", "relative path to the download endpoint below"],
    ])
ENDPOINT("GET", "/lesson-notes/{lessonNoteId}/attachments/page", "Same as above, paginated.", access="Same as above")
ENDPOINT("GET", "/lesson-notes/{lessonNoteId}/attachments/{attachmentId}/download", "Download the actual file.", access="Same as above",
    notes=["Returns the raw PDF bytes with a Content-Disposition: attachment header — point "
           "a browser download / <a href> directly at this URL (with the auth header attached, "
           "e.g. via a fetch()+blob approach, since a plain <a> tag can't set custom headers)."])
ENDPOINT("DELETE", "/lesson-notes/{lessonNoteId}/attachments/{attachmentId}", "Delete an attachment.", access="ADMIN or INSTRUCTOR")
ENDPOINT("PUT", "/lesson-notes/{lessonNoteId}/attachments/{attachmentId}", "Replace an attachment with a new file.", access="ADMIN or INSTRUCTOR",
    request=[["file", "file (multipart/form-data)", "yes", "same constraints as upload"],
             ["description", "string", "no", ""]])

# =====================================================================
# LESSON QUESTIONS
# =====================================================================
d.H(1, "Lesson Questions (student Q&A)")
d.P("A student asks a question (optionally targeted at a specific instructor); an "
    "instructor responds; status moves through a small workflow with a full audit history.")

ENDPOINT("POST", "/lesson-questions", "Submit a question.", access="STUDENT only",
    request=[
        ["subject", "string", "yes", "5-255 chars"],
        ["questionBody", "string", "yes", "10-3000 chars"],
        ["assignedInstructorId", "number", "no", "InstructorProfile.id, if the student wants to target a specific instructor"],
    ])
ENDPOINT("POST", "/lesson-questions/{id}/respond", "Respond to a question.", access="INSTRUCTOR only",
    request=[["response", "string", "yes", "10-3000 chars"]])
ENDPOINT("PUT", "/lesson-questions/{id}/status", "Change a question's status.", access="INSTRUCTOR or ADMIN",
    request=[["newStatus", "enum", "yes", "PENDING, IN_PROGRESS, ANSWERED, CLOSED"],
             ["changeReason", "string", "no", ""]])
ENDPOINT("GET", "/lesson-questions/{id}", "Get a question by ID.", access="Any authenticated user with access")
ENDPOINT("GET", "/lesson-questions/my-questions", "Paginated: my own submitted questions.", access="STUDENT only")
ENDPOINT("GET", "/lesson-questions/assigned", "Paginated: questions assigned to me.", access="INSTRUCTOR only")
ENDPOINT("GET", "/lesson-questions/status/{status}", "Paginated: questions filtered by status.", access="INSTRUCTOR or ADMIN",
    params=[["status", "enum (path segment)", "yes", "PENDING, IN_PROGRESS, ANSWERED, or CLOSED"]])
ENDPOINT("GET", "/lesson-questions/pending", "Paginated: my pending questions.", access="INSTRUCTOR only")
ENDPOINT("GET", "/lesson-questions/{id}/history", "Full status-change audit history.", access="Any authenticated user with access")
ENDPOINT("GET", "/lesson-questions/{id}/history/paginated", "Same as above, paginated.", access="Same as above")

# =====================================================================
# LIVE SESSIONS
# =====================================================================
d.H(1, "Live Sessions (virtual classes)")
d.P("A scheduled online class (theory lecture, Q&A, etc.) with a meeting link, distinct "
    "from a practical Booking — students register, attendance is tracked separately.")

ENDPOINT("POST", "/live-sessions", "Schedule a live session.", access="ADMIN or INSTRUCTOR",
    request=[
        ["instructorId / schoolId", "number", "yes", ""],
        ["title", "string", "yes", "max 200 chars"],
        ["description", "string", "no", "max 2000 chars"],
        ["scheduledAt", "datetime", "yes", "must be in the future"],
        ["durationMinutes", "number", "yes", "positive"],
        ["meetingUrl", "string", "no", "max 500 chars, e.g. a Zoom/Meet link"],
        ["maxParticipants", "number", "no", "positive, omit for unlimited"],
    ])
ENDPOINT("GET", "/live-sessions/{id}", "Get a session by ID.", access="ADMIN, INSTRUCTOR, or STUDENT")
ENDPOINT("PUT", "/live-sessions/{id}/status", "Update session status.", access="ADMIN or INSTRUCTOR",
    params=[["status", "enum (query param)", "yes", "SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED"]],
    notes=["Sent as a query parameter, e.g. PUT /live-sessions/12/status?status=IN_PROGRESS — "
           "not a JSON body."])
ENDPOINT("GET", "/live-sessions/school/{schoolId}/upcoming", "List sessions in the next 7 days for a school.", access="ADMIN, INSTRUCTOR, or STUDENT",
    notes=["This is the endpoint for a student's \"upcoming classes\" widget."])
ENDPOINT("POST", "/live-sessions/{id}/register", "Register a student for a session.", access="ADMIN or STUDENT",
    request=[["studentId", "number", "yes", "ignored/overridden for STUDENT callers, who always register themselves"]],
    notes=["Rejected with 400 if already registered, or if the session is full "
           "(maxParticipants reached)."])
ENDPOINT("PUT", "/live-sessions/{id}/attendance/{studentId}/present", "Mark a student present.", access="ADMIN or INSTRUCTOR")
ENDPOINT("GET", "/live-sessions/{id}/attendance", "List attendance for a session.", access="ADMIN or INSTRUCTOR",
    response=[["status", "enum", "REGISTERED, PRESENT, ABSENT, LATE"],
              ["checkedInAt", "datetime or null", ""]])

# =====================================================================
# LICENSE WORKFLOW
# =====================================================================
d.H(1, "License Progress Workflow")
d.P("Tracks a student's progression toward getting their license through a fixed sequence "
    "of stages. Some transitions happen automatically (e.g. passing a linked quiz); others "
    "require explicit instructor/admin action.")
d.P("Stages, in order: THEORY_LEARNING → THEORY_COMPLETED → QUIZ_PASSED → "
    "ROAD_TRAINING_STARTED → ROAD_TRAINING_IN_PROGRESS → ROAD_READY → "
    "DVLA_PROCESSING → LICENSE_APPROVED.")

ENDPOINT("GET", "/progress/license/students/{studentId}", "Get a student's workflow state.", access="ADMIN, INSTRUCTOR, or the STUDENT themselves",
    response=[
        ["currentStage", "enum", "see the sequence above"],
        ["theoryProgressPercent", "number", "0-100"],
        ["roadTrainingHours", "number", ""],
        ["stageUpdatedAt", "datetime", ""],
        ["approvedByInstructorId", "number or null", ""],
        ["notes", "string", ""],
    ])
ENDPOINT("POST", "/progress/license/students/{studentId}/initialize", "Start the workflow for a student.", access="ADMIN only",
    notes=["Rejected with 400 if a workflow already exists for this student — one per student, created once."])
ENDPOINT("PUT", "/progress/license/students/{studentId}/theory-progress", "Update theory progress percentage.", access="ADMIN or the STUDENT themselves",
    params=[["progressPercent", "number (query param)", "yes", "0-100"]],
    notes=["Query param, not a JSON body: PUT .../theory-progress?progressPercent=75"])
ENDPOINT("POST", "/progress/license/students/{studentId}/quiz-passed", "Mark the quiz stage passed.", access="ADMIN only",
    notes=["Not meant to be called directly from a normal frontend flow — this happens "
           "automatically when QuizService.submit() records a genuine passing attempt on a "
           "quiz linked to the student's workflow. It's ADMIN-only specifically so a student "
           "can't call it themselves to skip taking the quiz."])
ENDPOINT("PUT", "/progress/license/students/{studentId}/advance", "Advance to the next stage.", access="ADMIN or INSTRUCTOR",
    request=[["targetStage", "enum", "yes", "must be the next stage in sequence, or a later one where the API allows a manual jump"],
             ["notes", "string", "no", ""]],
    notes=["Rejected with 400 for: skipping a stage that requires automated progression "
           "(e.g. you can't manually set QUIZ_PASSED — it only happens via the quiz "
           "submission path above), regressing to an earlier stage, or an otherwise invalid "
           "transition. Build the \"advance stage\" UI to only offer the actual next valid "
           "stage, not a free-form dropdown of all eight."])

# =====================================================================
# DRIVING ASSESSMENTS
# =====================================================================
d.H(1, "Driving Assessments")
d.P("An instructor records a formal pass/fail practical driving assessment for one of "
    "their students, optionally linked to the specific booking it was for. This is "
    "separate from a lesson note (free-form coaching feedback) — an assessment is a "
    "scored, official record, and a PASSED result also drives the student's gamification "
    "points/badges (see the Gamification section).")

ENDPOINT("POST", "/driving-assessments", "Record a driving assessment for a student.", access="INSTRUCTOR only",
    request=[
        ["studentId", "number", "yes", "StudentProfile.id"],
        ["bookingId", "number", "no", "if provided, must actually belong to this instructor+student pair"],
        ["assessmentDate", "datetime", "yes", ""],
        ["score", "number", "yes", "0-100"],
        ["result", "enum", "yes", "PENDING, PASSED, FAILED, NEEDS_IMPROVEMENT"],
        ["feedback", "string", "no", "max 2000 chars"],
        ["durationMinutes", "number", "no", "≥ 1"],
    ],
    example_req='{\n  "studentId": 15,\n  "bookingId": 88,\n  "assessmentDate": "2026-08-20T15:00:00",\n  "score": 82,\n  "result": "PASSED",\n  "feedback": "Confident on roundabouts, watch following distance on the motorway."\n}',
    response=[
        ["id / studentId / studentName / instructorId / instructorName / bookingId", "", "bookingId is null if not linked to a booking"],
        ["assessmentDate / score / result / feedback / durationMinutes", "", ""],
        ["createdAt / updatedAt", "", ""],
    ],
    notes=["The student is automatically sent an IN_APP notification with the result — "
           "wording differs by PASSED / FAILED / NEEDS_IMPROVEMENT / PENDING.",
           "A PASSED result also awards gamification points and the \"Road Ready\" badge "
           "(once per student, not once per assessment) — see the Gamification section."])

ENDPOINT("PATCH", "/driving-assessments/{id}/feedback", "Edit the feedback text on an assessment.", access="INSTRUCTOR only (the authoring instructor)",
    request=[["feedback", "string", "yes", "max 2000 chars"]],
    notes=["ONLY the feedback field can be edited — score, result, assessmentDate, and the "
           "booking link are immutable after creation. There is no delete endpoint. This is "
           "deliberate: score/result already triggered a one-time notification and "
           "gamification award at creation time, so the API doesn't expose a way to change "
           "the outcome after the fact. Don't build an \"edit score\" or \"delete assessment\" "
           "control — only an \"edit feedback\" one."])

ENDPOINT("GET", "/driving-assessments/{id}", "Get an assessment by ID.", access="ADMIN, the authoring INSTRUCTOR, or the assessment's STUDENT")
ENDPOINT("GET", "/driving-assessments/student/{studentId}", "List a student's assessments.", access="ADMIN, the student themselves, or an instructor who has assessed that student")
ENDPOINT("GET", "/driving-assessments/instructor/{instructorId}", "List an instructor's authored assessments.", access="ADMIN or the instructor themselves")

# =====================================================================
# NOTIFICATIONS
# =====================================================================
d.H(1, "Notifications")
d.P("Two things happen here: the system automatically sends notifications for certain "
    "events, and admins/instructors can manually send an arbitrary notification via "
    "POST /send. Automatic IN_APP notifications currently fire on: a booking being "
    "created, a booking being cancelled, a quiz being submitted (pass or fail), a new "
    "lesson note being added, and a driving assessment being recorded. All of these are "
    "fire-and-forget from the frontend's perspective — nothing needs to be called to "
    "trigger them, they're side effects of the endpoints above.")

ENDPOINT("POST", "/notifications/send", "Send a notification to a user.", access="ADMIN or INSTRUCTOR",
    request=[
        ["userId", "number", "yes", "the raw User.id (not a profile id) of the recipient"],
        ["subject", "string", "yes", "max 200 chars"],
        ["body", "string", "yes", ""],
        ["channel", "enum", "yes", "EMAIL, SMS, PUSH, IN_APP"],
        ["recipientAddress", "string", "no", "overrides the default recipient address (the user's own email) — rarely needed"],
    ],
    response=[["status", "enum", "PENDING, SENT, or FAILED — see the important note below"]],
    notes=["IMPORTANT — EMAIL is asynchronous: the response comes back with status "
           "PENDING immediately (the actual send happens moments later on a background "
           "thread, so the caller isn't stuck waiting on a slow mail server). If you display "
           "delivery status in a UI, don't treat PENDING as final — either poll GET "
           "/notifications/me afterward, or simply don't surface real-time delivery status "
           "for EMAIL at all (the common choice).",
           "IN_APP is synchronous and settles to SENT immediately in the same response.",
           "SMS and PUSH channels are not currently wired to a real provider — they always "
           "come back as FAILED. Don't offer these as user-facing channel choices yet."])

ENDPOINT("GET", "/notifications/me", "My own notifications, most recent first.", access="Any authenticated user",
    notes=["This is the endpoint for a bell-icon notification list / inbox."])
ENDPOINT("PATCH", "/notifications/{id}/read", "Mark a notification as read.", access="Any authenticated user (their own notification only)")

# =====================================================================
# GAMIFICATION
# =====================================================================
d.H(1, "Gamification")
d.P("Student-only (instructors have no gamification data of their own). Points, a weekly "
    "lesson streak, and a fixed set of named badges, plus a school-wide leaderboard. "
    "Nothing here is ever called directly to \"award\" anything — it's entirely a "
    "side effect of normal actions elsewhere in the API (completing a booking, passing a "
    "quiz, passing a driving assessment). These endpoints are read-only from the frontend's "
    "perspective.")

d.H(2, "How points are earned")
d.T(["Trigger", "Points", "Notes"], [
    ["Booking marked COMPLETED", "10", "awarded once per booking"],
    ["Quiz submission passed", "20", "awarded once per quiz per student — retaking an "
     "already-passed quiz does not award points again"],
    ["Driving assessment result = PASSED", "30", "awarded once per student (the first "
     "passed assessment); later passed assessments don't award again"],
])

d.H(2, "Weekly streak")
d.P("Counts consecutive calendar weeks (Monday-start) in which the student had at least "
    "one completed booking. Completing a second lesson in the same week doesn't increase "
    "the streak further; missing a week resets it back to 1 on the next completed lesson "
    "(it does not silently decay to 0 while the student is simply inactive — nothing "
    "changes until their next completed lesson).")

d.H(2, "Badges")
d.P("A fixed catalog, not admin-configurable. Each is awarded at most once per student:")
d.T(["Badge", "Awarded when"], [
    ["First Lesson", "First booking ever marked completed"],
    ["Quiz Master", "First quiz ever passed"],
    ["Road Ready", "First driving assessment ever passed"],
    ["5-Week Streak", "Weekly streak reaches 5"],
    ["10-Week Streak", "Weekly streak reaches 10"],
    ["Century Club", "Total points reach 100"],
    ["High Achiever", "Total points reach 500"],
])

ENDPOINT("GET", "/gamification/me", "The current student's own gamification summary.", access="STUDENT only",
    response=[
        ["studentId / studentName", "", ""],
        ["totalPoints / currentStreakWeeks / longestStreakWeeks", "number", ""],
        ["schoolRank", "number", "1-based rank by totalPoints within the student's own school"],
        ["badges", "array", "each item: badge (enum), displayName, description, awardedAt"],
    ],
    example_resp='{\n  "success": true,\n  "message": "Operation successful",\n  "data": {\n    "studentId": 15,\n    "studentName": "Jamie Jones",\n    "totalPoints": 60,\n    "currentStreakWeeks": 3,\n    "longestStreakWeeks": 3,\n    "schoolRank": 4,\n    "badges": [\n      {"badge": "FIRST_LESSON", "displayName": "First Lesson", "description": "Completed your first practical lesson", "awardedAt": "2026-07-02T10:15:00"}\n    ]\n  }\n}',
    notes=["This is the endpoint for a student's own \"my progress\" / points-and-badges "
           "widget."])

ENDPOINT("GET", "/gamification/students/{studentId}", "Another student's gamification summary.", access="ADMIN, an INSTRUCTOR in the same school, or the STUDENT themselves",
    notes=["Same response shape as /gamification/me. Use this for an instructor-facing "
           "student detail screen, not for a student's own widget (use /me for that, it "
           "resolves the caller's own profile automatically)."])

ENDPOINT("GET", "/gamification/leaderboard/school/{schoolId}", "School-wide leaderboard, ranked by total points.", access="ADMIN, or an INSTRUCTOR/STUDENT in that same school",
    response=[["Paginated (see the Pagination convention above) — each row:", "", ""],
              ["rank / studentId / studentName / totalPoints / currentStreakWeeks", "", ""]],
    notes=["An INSTRUCTOR or STUDENT requesting a DIFFERENT school's leaderboard gets 400, "
           "not the data — always pass the caller's own schoolId (from GET /auth/me)."])

# =====================================================================
# THINGS THE FRONTEND NEEDS TO BUILD
# =====================================================================
d.H(1, "Checklist: Things the Frontend Needs to Build")
d.P("A summary of implementation requirements that aren't just \"call an endpoint\" — "
    "read this before starting, it'll save rework.")

d.H(2, "1. Auth token lifecycle")
d.BULLETS([
    "Store accessToken and refreshToken after login (e.g. in memory + a secure storage "
    "mechanism appropriate for your platform — avoid plain localStorage for the refresh "
    "token if you can help it, given it's valid for 7 days).",
    "Attach Authorization: Bearer <accessToken> to every request except the public auth "
    "endpoints listed at the top of this document.",
    "On a 401, call POST /auth/refresh-token once, retry the original request with the new "
    "access token; if that also fails, force the user back to the login screen.",
    "Call POST /auth/logout on explicit sign-out to revoke the refresh token server-side, "
    "then clear local storage.",
])

d.H(2, "2. Fetch and cache profile IDs right after login")
d.BULLETS([
    "Call GET /auth/me immediately after login and cache studentProfileId / "
    "instructorProfileId / schoolId — these (not the raw user ID) are what most other "
    "endpoints expect.",
])

d.H(2, "3. Role-aware UI")
d.BULLETS([
    "Three distinct app experiences: ADMIN (school/user/vehicle management, cross-cutting "
    "visibility), INSTRUCTOR (their own students/courses/bookings/lesson notes), STUDENT "
    "(their own courses/quizzes/bookings/progress).",
    "ADMIN is itself two different dashboards, not one — check GET /auth/me's "
    "bootstrapAdmin flag. The bootstrap admin manages ALL schools/admins (school "
    "create/delete, the deletion-request review queue); a regular admin only ever sees "
    "their own school and has no delete button at all, only a \"request deletion\" one. "
    "Don't build a single generic \"admin\" screen that tries to cover both.",
    "Remember bookings are instructor-initiated — don't build a student-facing \"book a "
    "lesson\" button; build an instructor-facing \"schedule a lesson for my student\" flow "
    "instead.",
])

d.H(2, "4. File uploads are multipart, and PDF-only")
d.BULLETS([
    "Only the lesson-note attachment endpoints (upload and replace) take multipart/form-data. "
    "Every other endpoint in this API is JSON.",
    "50 MB max, PDF only, enforced server-side — validate client-side too for a faster "
    "error message, but don't rely on client-side validation alone.",
])

d.H(2, "5. Multi-step flows the frontend orchestrates")
d.BULLETS([
    "\"Instructor books a lesson\": needs the student's StudentProfile.id (populate the "
    "picker from GET /students/school/{schoolId} using the instructor's own schoolId — no "
    "need to make the instructor type a raw student ID) and the instructor's own "
    "InstructorProfile.id (from /auth/me), plus optionally a vehicle ID from a vehicle-list "
    "call. There's no single \"book by student email\" convenience endpoint.",
    "\"Student checks license progress\": GET /progress/license/students/{studentId} using "
    "their own cached studentProfileId.",
    "\"Instructor plans a route for today's lesson\": needs an existing bookingId first "
    "(create/confirm the booking, then generate the route against it).",
])

d.H(2, "6. Quiz-taking UX")
d.BULLETS([
    "Fetching a quiz for a student to take: GET /quizzes/{id} with forStudent=true (or just "
    "omit the parameter, that's the default) — correct answers are stripped.",
    "An instructor/admin authoring or reviewing a quiz should explicitly pass "
    "forStudent=false to see answers.",
    "Submitting is a separate call (POST .../submit); there's a maxAttempts limit enforced "
    "server-side — disable the submit button client-side once the student has exhausted "
    "their attempts (check attemptNumber from prior submissions) to avoid a confusing 400.",
])

d.H(2, "7. Error handling")
d.BULLETS([
    "Check the success boolean in every response body, not just the HTTP status.",
    "Surface the message field to the user on failure — these are written to be "
    "human-readable (e.g. \"Instructor and student must belong to the same school\"), not "
    "generic codes.",
])

d.H(2, "8. CORS")
d.BULLETS([
    "The backend only accepts requests from origins listed in its CORS_ALLOWED_ORIGINS "
    "config — currently set to localhost dev origins. Once the frontend has a real "
    "deployed URL, that's a BACKEND-side config change (an environment variable + a "
    "redeploy) that the backend team needs to make — it isn't something the frontend "
    "can work around on its own.",
])

d.H(2, "9. Environment configuration")
d.BULLETS([
    "The only thing the frontend needs to configure is the backend's base URL. All "
    "third-party credentials (Cloudinary, the mail relay, the routing service, error "
    "tracking) are server-side only — no API keys belong in frontend code or environment "
    "config for this integration.",
])

d.H(2, "10. Gamification and driving-assessment UI (student-only)")
d.BULLETS([
    "Gamification is STUDENT-facing only — don't build any gamification UI in the "
    "instructor or admin experience beyond the read-only \"view a student's summary\" "
    "screen (GET /gamification/students/{studentId}).",
    "Points/streak/badges update as a side effect of other actions (booking completed, "
    "quiz passed, assessment passed) — there's no \"refresh my points\" action to build, "
    "just re-fetch GET /gamification/me after those actions if you want the UI to reflect "
    "the change immediately.",
    "Driving assessments are instructor-authored, like lesson notes, but are NOT editable "
    "beyond the feedback text (no score/result edit, no delete) — don't reuse the "
    "lesson-note edit/delete UI pattern for assessments.",
])

d.save("Frontend_API_Guide.docx")
print("saved Frontend_API_Guide.docx")
