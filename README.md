# CampusConnect

CampusConnect lets campus members discover and register for events, report lost or found items, and search the public listings. Organizers publish events and track attendance. Administrators manage roles and moderate both listings.

**IT342 · Project Development Increment 3 · Jehryn D. Laurino · G01**

**Deadline** December 2, 2026 at 11:59 PM Philippine time (UTC+8).

[Increment 3 completion report](docs/Increment-3-Completion-Report.pdf) · [Editable DOCX](docs/Increment-3-Completion-Report.docx) · [Approved SRS](docs/IT342_G01_Laurino_Jehryn_ProjectName_SRS.pdf) · [Verification results by SRS ID](TESTING.md) · [Increment 2 report](docs/Increment-2-Completion-Report.pdf)

## Continue on macOS

Use JDK 21, Maven 3.9+, Node.js with npm, and either native PostgreSQL 16 and Mailpit or the supplied Docker Compose services. Run these commands from the repository root. On this Mac the JDK is bundled with Android Studio. For a separately installed JDK use `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"`.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
mvn -v
node -v
npm -v
```

The private `.env` is already configured for local use on Jehryn’s Mac. On a fresh checkout, create it once, choose unique database and bootstrap administrator passwords, and enter them locally. The bootstrap settings create an administrator on the first start.

```sh
(umask 077 && test ! -e .env && cp .env.example .env)
open -e .env
```

1. **Database and mail.** With Homebrew installed, run the following in one terminal. The script creates a private cluster and inbox under the ignored `.campusconnect-local/` directory and binds both services to loopback. Ctrl-C stops the services. Restart the same script to reuse the database. It uses the default local `DB_URL` from the example. For Docker, use `docker compose --env-file .env up -d` in place of the two commands below.

   ```sh
   brew install postgresql@16 mailpit
   bash scripts/start-mac-services.sh
   ```

2. **Backend.** In a second terminal, select JDK 21 as above, then start Spring Boot.

   ```sh
   SERVER_ADDRESS=127.0.0.1 bash scripts/start-backend.sh
   ```

   If this reports that CampusConnect is already running, continue to the frontend. To apply backend or `.env` changes, stop the existing backend first.

3. **Frontend.** In a third terminal, install the locked dependencies and start Vite.

   ```sh
   cd frontend
   npm ci
   npm run dev -- --host 127.0.0.1 --strictPort
   ```

Open [CampusConnect](http://localhost:5173), [backend health through Vite](http://localhost:5173/api/health), or the [local Mailpit inbox](http://localhost:8025). The backend reads `.env` at startup. Restart it after changing provider values. After initial administrator setup, both `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` may be cleared together. Changing `DB_PASSWORD` alone does not rotate an existing database role’s password.

### Windows startup

Use the same private `.env`, run `docker compose --env-file .env up -d`, then `.\scripts\start-backend.ps1` from PowerShell. In a second terminal use `cd frontend`, `npm ci`, and `npm run dev`. Browser-test commands for both platforms are in [TESTING.md](TESTING.md).

## Provider configuration

Edit the root `.env` privately. Keep the Cloudinary values on the backend. Google’s Maps JavaScript API uses a browser-visible key. Restrict that key by API and permitted website referrers.

| Setting | Where to obtain the value |
| --- | --- |
| `GOOGLE_MAPS_API_KEY` | In Google Maps Platform, select a project and obtain a Maps JavaScript API key. For a standard key, restrict the API to Maps JavaScript API and the website to `http://localhost:5173/*`. Use the same hostname in the browser. [Google setup](https://developers.google.com/maps/documentation/javascript/get-api-key) |
| `GOOGLE_MAP_ID` | Use a JavaScript map ID from Google Cloud Console → Google Maps Platform → Map Management. [Google map IDs](https://developers.google.com/maps/documentation/javascript/map-ids/get-map-id) |
| `CAMPUS_SOUTH`, `CAMPUS_NORTH`, `CAMPUS_WEST`, `CAMPUS_EAST` | Enter the approved campus boundary’s minimum/maximum latitude and longitude in decimal degrees. The example rectangle was used for local validation. Campus approval is pending. |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Select the intended Cloudinary product environment. Copy its cloud name and the key/secret pair from the environment’s API Keys settings. [Cloudinary credential guide](https://cloudinary.com/documentation/developer_onboarding_faq_find_credentials) |

Use existing approved quota for a live demonstration. Obtain the account owner’s approval before enabling billing or incurring charges. For the live pin and photo acceptance steps, see [TESTING.md](TESTING.md#live-provider-acceptance).

## Instructor demo

1. Open the app in separate organizer, member, and administrator browser profiles. The Mac demo accounts are `organizer.demo@example.test` and `member.demo@example.test`. Read their passwords locally in `.campusconnect-local/demo-accounts.txt`. The administrator login and password are in the private `.env`. On a fresh database, register two members and promote one through **User management**.
2. As organizer, create a future event with capacity one, save the draft, and publish it. Find it as a visitor by category and date. As member, register. In **My events & counts**, show one active registration. Cancel the event and show the updated status in **My registrations**.
3. As member, create a FOUND report with a description, incident time, and location. Enter an in-bounds pin, save, then reload. Search as a visitor. Show photo validation with a text file. With approved provider settings, repeat the live acceptance checks.
4. Mark the report resolved. As administrator, hide it through **Moderate reports**. Refresh the visitor search to show it has disappeared. For the account regression, request a password reset for a disposable member and follow the email in Mailpit.

## Verification record

**Windows checkpoint, November 26.** All 15 backend tests and the React build passed. Chrome checked the workflows against PostgreSQL 16 and a local SMTP receiver, including the final-place race and a Spring Boot restart. [Seven original screenshots](docs/increment3-screenshots)

**Mac, November 27.** All 15 backend tests passed and Vite built 55 modules. Chrome ran against fresh PostgreSQL 16 and Mailpit. Two members competed for one place, cancellation released it, and the persisted records reopened after restarting Spring Boot. Live Google tile/marker rendering and Cloudinary upload/display/removal still require approved provider access. [Mac checks and evidence](TESTING.md)

## Account security from Increments 1 and 2

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
