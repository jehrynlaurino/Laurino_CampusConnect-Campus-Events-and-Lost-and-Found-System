# CampusConnect – Increment 3 development checkpoint

This checkpoint adds campus events, event registration, lost-and-found reports, photo handling, map locations, and administrator moderation. The [approved SRS](docs/IT342_G01_Laurino_Jehryn_ProjectName_SRS.pdf) and [seven browser screenshots](docs/increment3-screenshots) are in the repository.

### Continue on macOS

Use JDK 21, Maven, Node.js with npm, and a running Docker installation with Compose. From a fresh clone, copy the environment example and edit your local database and first-run administrator credentials.

```sh
cp .env.example .env
open -e .env
```

After saving the file, start PostgreSQL and Mailpit, then start the backend in the same terminal.

```sh
docker compose --env-file .env up -d
bash scripts/start-backend.sh
```

In a second terminal, open the cloned repository and run:

```sh
cd frontend
npm ci
npm run dev -- --host 127.0.0.1
```

Open the local address printed by Vite. Browse `/events` or `/reports`. To create an event, use **User Management** as the bootstrap administrator to grant a registered account the **ORGANIZER** role. A **MEMBER** can register once the organizer publishes the event.

### Results and remaining work

All 15 backend tests passed and the React production build succeeded. The Chrome browser run used real PostgreSQL 16 and a local SMTP receiver. It checked the race for the last event place, report ownership, moderation, upload errors, password recovery, and persistence after restarting Spring Boot. The Windows-specific verification harness is in [scripts/verify_increment3.py](scripts/verify_increment3.py). The Maven tests and frontend build can also be run on macOS.

Live Google Maps rendering and successful Cloudinary uploads still need provider credentials. Set the Google browser key, map ID, approved campus bounds, and Cloudinary settings in your private `.env` before those checks. The separate Increment 3 completion PDF and final submission documentation are pending. Optional claims and related-report suggestions are deferred.

## Increment 2 record

**CampusConnect: Campus Events and Lost-and-Found System**  
**IT342 • Systems Integration and Architecture 1**  
**Increment 2: Profile Management and Password Security**

This is the React + Spring Boot + PostgreSQL implementation of **SRS FR-001 to FR-005**, including the Increment 2 profile and password-security work.

## Increment 2 additions

### Profile management

Every authenticated MEMBER, ORGANIZER, and ADMIN can open **My Account**, view their own persisted full name, registered email, and assigned role, and change only their full name. The backend derives the account exclusively from the signed-in session; the registered email and role are intentionally read-only because the approved SRS does not specify email-change verification. Profile API responses never contain password hashes, account-status controls, or privileged fields.

### Secure forgot/reset password

The login screen now links to a working **Forgot password?** page. `POST /api/auth/forgot-password` returns the same generic message for every valid request to prevent account enumeration. For an active account it sends a CampusConnect-branded email through Spring Mail using a trusted configured frontend URL. Docker Compose includes Mailpit for local delivery and viewing at `http://localhost:8025`.

Reset tokens use 256 bits of `SecureRandom` entropy and only their SHA-256 hashes are stored. A token lasts 15 minutes, is single-use, is atomically consumed with the password update, and a new request invalidates prior outstanding tokens. Invalid, expired, and used links are rejected. Recovery requests and reset attempts have separate 15-minute in-memory rate limits. Tokens and plaintext passwords are redacted from DTO log representations and are never returned in API responses.

### Change password and session invalidation

The authenticated **Change password** page requires the current password, a different strong new password, and matching confirmation. Password changes and resets use BCrypt, invalidate all active reset tokens, and invalidate the current and other authenticated sessions. A persisted credential-version check backs up Spring Security’s session registry so an older session is rejected on its next request even if a container did not register it in the session registry.

## Implemented features (real database, not mock accounts)

| SRS ID | Implemented functionality |
|---|---|
| FR-001 | Member registration; required-field validation; email normalization; password confirmation and complexity checks; unique database persistence; BCrypt password hashing |
| FR-002 | Sign in with registered email/password; secure server-side session cookie; invalid login response |
| FR-003 | Sign out (invalidated server session); edit own full name; change password after checking current password |
| FR-004 | MEMBER, ORGANIZER, ADMIN dashboards with protected React routes **and** backend methods; all roles validated on every authenticated request |
| FR-005 | Admin views all accounts, assigns roles, activates/deactivates accounts; administrative API access restricted on server |

Members include students, faculty and staff. A public registration creates only `MEMBER`; an organizer or admin role must be granted by an existing administrator. **No hardcoded demo user or authentication shortcut exists.** First-run administrator is securely created in the database using environment variables; the password is stored as BCrypt, not plaintext.

Events, registrations, maps, Cloudinary and lost-and-found records are planned for later increments and **are not implemented in Increment 1**. The dashboard labels these future modules as upcoming; they are not fake buttons.

## Requirements

- Java **21** (JDK)
- Apache Maven **3.9+** (`mvn --version` should work)
- Node.js **20.19+ or 22+**, with npm
- Docker Desktop (for the PostgreSQL database and Mailpit email inbox) **or** a locally installed PostgreSQL 16 server plus SMTP service
- Git (for your individual repository)

## Quick setup (Windows PowerShell)

Open the project folder in VS Code or IntelliJ:

1. Copy `.env.example` to `.env`. Change `DB_PASSWORD` to a unique local development password. Set `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` to credentials **you choose and keep private**. Passwords must be 10-72 characters and include uppercase, lowercase, a number, and a symbol. Optionally set `APP_ALLOWED_EMAIL_DOMAIN` after confirming your actual campus domain; leave it empty if you need to demo with test email addresses.
2. Start **Docker Desktop**. From the project root, run:
   ```powershell
   docker compose --env-file .env up -d
   docker compose ps
   ```
   This starts PostgreSQL on port `5432`, Mailpit SMTP on port `1025`, and the Mailpit inbox at **http://localhost:8025**. For a local PostgreSQL installation instead, manually create a database `campusconnect` and a role `campusconnect` with the password set in `.env`; configure a real SMTP service for password-reset delivery.
3. Start the Spring Boot backend in a new PowerShell terminal:
   ```powershell
   .\scripts\start-backend.ps1
   ```
   If your PowerShell policy blocks scripts, run `Set-ExecutionPolicy -Scope Process Bypass` in **that terminal only**, then rerun the script.
4. Start the React frontend in another terminal:
   ```powershell
   cd frontend
   npm install
   npm run dev
   ```
5. Open **http://localhost:5173**. Register a Member, log in, and test your dashboard, **My Account**, and **Change password**. Sign out. To demonstrate reset password, choose **Forgot password?**, then open the Mailpit inbox and follow the real emailed link. Then log in using the administrator credentials you chose, open **User Management**, and change your Member to `ORGANIZER`.
6. Refresh the Member's browser (or log out and in). They should see the Organizer dashboard. An Admin cannot be created via public registration.

### Mac / Linux

Run steps 1–2 above, then in one terminal `./scripts/start-backend.sh`; in another run `cd frontend && npm install && npm run dev`.

### First-run admin detail

The initial admin is created **only if an account with `APP_ADMIN_EMAIL` does not already exist**. To avoid leaving the plaintext bootstrap password in your environment, remove `APP_ADMIN_PASSWORD` from `.env` after you confirm the first admin was created, then restart the backend. It will never overwrite a previously saved account or reset its password. If you have an existing member account with the same email, it **will not be promoted** automatically. Change roles using an existing admin.

If you change `DB_PASSWORD` after PostgreSQL has already created its Docker volume, the existing PostgreSQL password does not change automatically. Either change the role password inside PostgreSQL or recreate the **development-only** volume after backing up any wanted data. Do not use `docker compose down -v` casually because it deletes database data.

## Architecture

```text
React (localhost:5173)
  |   (Vite /api proxy, cookies included)
  v
Spring Boot (localhost:8080)
  |--> Spring Security: HttpOnly session, CSRF protection, BCrypt, RBAC
  |--> Controllers --> AccountService --> UserRepository (JPA)
  v
PostgreSQL 16 (localhost:5432) -> app_users (real persisted accounts)
```

### Account fields stored

`id`, `full_name`, `email` (unique), `password_hash`, `role`, `active`, `created_at`, `credential_version`.

Increment 2 adds `password_reset_tokens` with `id`, `user_id` (foreign key), a unique SHA-256 `token_hash`, `created_at`, `expires_at`, and `used_at`, plus user and expiration indexes. The project retains JPA's non-destructive `ddl-auto=update` strategy, so an existing development database gains these columns/tables without dropping existing accounts. Back up a real database before schema updates.

### Endpoints

| Endpoint | Method | Permission | What it does |
|---|---|---|---|
| `/api/auth/csrf` | GET | Public | Fetch session-bound CSRF token for subsequent writes |
| `/api/auth/register` | POST JSON | Public + CSRF | Create MEMBER; requires matching `password` and `confirmPassword` |
| `/api/auth/login` | POST form-urlencoded | Public + CSRF | Check password, establish server session |
| `/api/auth/logout` | POST | Signed in + CSRF | Invalidate session |
| `/api/auth/me` | GET | Signed in | Return current persisted account, no password hash |
| `/api/auth/profile` | PUT JSON | Signed in + CSRF | Edit full name |
| `/api/auth/change-password` | PUT JSON | Signed in + CSRF | Verify current password and matching different new password, BCrypt-hash it, invalidate reset tokens and all sessions |
| `/api/auth/forgot-password` | POST JSON | Public + CSRF | Return a generic response, rate-limit requests, and email a secure reset link for an active matching account |
| `/api/auth/reset-password` | POST JSON | Public + CSRF | Atomically consume a valid 15-minute reset token, BCrypt-hash the new password, and invalidate all sessions |
| `/api/dashboard/member` | GET | MEMBER only | Role-authorized account dashboard |
| `/api/dashboard/organizer` | GET | ORGANIZER only | Role-authorized account dashboard |
| `/api/dashboard/admin` | GET | ADMIN only | Role-authorized account dashboard |
| `/api/admin/users` | GET | ADMIN only | List real users |
| `/api/admin/users/{id}/role` | PATCH JSON | ADMIN only | Set role |
| `/api/admin/users/{id}/status` | PATCH JSON | ADMIN only | Activate/deactivate |
| `/api/health` | GET | Public | Check Spring Boot is listening |

**Security:** Sessions are held on the backend and identified by an **HttpOnly**, `SameSite=Lax` cookie; the frontend never stores passwords or access tokens in localStorage. Spring Security's CSRF token must be sent in `X-CSRF-TOKEN` for POST, PUT and PATCH. Backend permission checks are enforced with `@PreAuthorize`, plus a database recheck of role and active status on each request. Deactivation invalidates the affected user's existing session on their next request, and the frontend clears its protected state on HTTP 401. The Vite proxy is intended for development only. For production, run with `SPRING_PROFILES_ACTIVE=prod` behind an HTTPS reverse proxy; `application-prod.yml` requires HTTPS and marks session cookies secure.

## Run tests

From the project root:

```bash
mvn -f backend/pom.xml test
cd frontend
npm install
npm run build
```

The backend integration suite has **12 H2-backed tests in PostgreSQL compatibility mode**. It verifies registration and persisted BCrypt hash; password complexity, confirmation and email normalization; rejection of duplicates/invalid fields; login/logout; MEMBER/ADMIN route separation; admin role promotion; immediate account deactivation; persisted own-profile updates; password changes; invalidation of a second active session; generic unknown-account recovery responses; reset email construction; token hashing, expiry, replacement, and single use; old-password rejection; and recovery/reset rate limits. `npm run build` checks the React production bundle.

### Actual local verification status (November 26, 2026)

- Backend test suite: passed — 12 tests, 0 failures, 0 errors after the Increment 2 session-invalidation fix.
- Frontend production build: passed — Vite transformed 49 modules and completed successfully in 510 ms; see `TESTING.md` for the result.
- A separate browser run exercised profile editing, password changes, SMTP delivery, password reset, and sign-in with the new password using H2 and a local SMTP receiver. Figures 1–9 appear in the [Increment 2 report](docs/Increment-2-Completion-Report.pdf). Verification with the Docker Compose PostgreSQL and Mailpit services remains pending.

### Demonstration checklist (do these live)

- [ ] PostgreSQL container running (`docker compose ps`)
- [ ] Backend responds at `http://localhost:8080/api/health`
- [ ] Register a real account; confirm it persists after backend restart
- [ ] Try the same email twice; second attempt shows duplicate error
- [ ] Show `app_users.password_hash` in PostgreSQL (BCrypt hash, never plaintext)
- [ ] Enter a wrong password, then log in correctly
- [ ] Open the MEMBER dashboard; attempt ADMIN endpoint and see HTTP 403
- [ ] Change profile name, refresh, and confirm the persisted value is still shown
- [ ] Change password and confirm an older second browser session is rejected
- [ ] Request a reset password link and inspect the real message in Mailpit; confirm an unknown email receives the same generic message
- [ ] Use the reset link once, confirm the old password fails, the new password succeeds, and a reused link is rejected
- [ ] Use admin account to promote Member to Organizer
- [ ] Confirm Organizer dashboard appears after refreshing/re-sign-in
- [ ] As admin, deactivate a user; their existing session becomes unauthorized
- [ ] Log out and confirm the protected page is no longer accessible

Inspect database row hashes (never show your `.env` or passwords on a recording):

```bash
docker compose exec db psql -U campusconnect -d campusconnect \
  -c 'SELECT id,email,role,active,password_hash FROM app_users;'
```

## GitHub submission (important)

**Your instructor's actual cutoff is listed as November 19, 2026 at 9:00 PM, even though the assignment header says 9:30 PM. Use 9:00 PM as the safe deadline.** All source files must be **committed AND pushed before cutoff**; local commits alone do not count.

1. Create a new **empty** repository under your individual GitHub account (e.g., `CampusConnect`). Do not select "Initialize with README" because this project already contains one.
2. In the **project root**, run (replace your GitHub username):

   ```bash
   git init
   git add .
   git commit -m "feat(auth): implement registration and BCrypt passwords"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/CampusConnect.git
   git push -u origin main
   ```

   If you add fixes after testing, commit them meaningfully (`fix(auth): correct session handling`) and push again before cutoff.
3. **Never commit `.env`**, private passwords, access tokens, database volumes, or secret API keys. `.gitignore` excludes them.
4. Confirm your GitHub page shows the latest code and pushed commit timestamps. Open the repository URL in an incognito window to verify it exists.
5. Submit your repository link using the provided **Google Form**: https://forms.gle/yvRC4QJQu3kDhX718

## Troubleshooting

- **`mvn` not found**: install Apache Maven 3.9+ and add its `bin` folder to PATH; reopen terminal.
- **`java` not found**: install JDK 21 and configure `JAVA_HOME`.
- **Port 5432 in use**: stop your other PostgreSQL process or adjust the DB and Compose port together.
- **Backend cannot connect to database**: ensure Docker Desktop is running and `DB_PASSWORD` matches the database password.
- **Frontend shows server unreachable**: backend must be running at `http://localhost:8080` first.
- **HTTP 403 CSRF**: use the React UI (which fetches the CSRF token). For Postman, first GET `/api/auth/csrf` with saved cookies and send `X-CSRF-TOKEN` to POST/PUT/PATCH.
- **Account has old role**: refresh the page or sign out and in; backend permissions are re-checked against the database on every request.
- **Already-used admin email**: bootstrap does not override it; use the existing admin to assign roles.

## Requirements not delivered in Increment 1

The rest of the SRS (FR-006–FR-037) is intentionally reserved for later development. This increment is a working authentication foundation, **not yet the complete CampusConnect application**.

## Development note

This source package is intended to be built and run on a computer with the prerequisites above. It is not a live deployed service. Run the tests and demo checklist, and fix environment-specific issues before claiming it as fully tested or submitting it.
