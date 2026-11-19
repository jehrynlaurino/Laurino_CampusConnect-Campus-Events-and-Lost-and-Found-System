# CampusConnect – Project Increment 1

**CampusConnect: Campus Events and Lost-and-Found System**  
**IT342 • Systems Integration and Architecture 1**  
**Increment 1: User Accounts and Authentication**

This is the React + Spring Boot + PostgreSQL implementation of **SRS FR-001 to FR-005**.

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
- Docker Desktop (for the PostgreSQL database) **or** a locally installed PostgreSQL 16 server
- Git (for your individual repository)

## Quick setup (Windows PowerShell)

Open the project folder in VS Code or IntelliJ:

1. Copy `.env.example` to `.env`. Change `DB_PASSWORD` to a unique local development password. Set `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` to credentials **you choose and keep private**. Passwords must be 10-72 characters and include uppercase, lowercase, a number, and a symbol. Optionally set `APP_ALLOWED_EMAIL_DOMAIN` after confirming your actual campus domain; leave it empty if you need to demo with test email addresses.
2. Start **Docker Desktop**. From the project root, run:
   ```powershell
   docker compose --env-file .env up -d
   docker compose ps
   ```
   For a local PostgreSQL installation instead, manually create a database `campusconnect` and a role `campusconnect` with the password set in `.env`; skip Docker.
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
5. Open **http://localhost:5173**. Register a Member, log in, and test your dashboard. Sign out. Then log in using the administrator credentials you chose, open **User Management**, and change your Member to `ORGANIZER`.
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

`id`, `full_name`, `email` (unique), `password_hash`, `role`, `active`, `created_at`.

### Endpoints

| Endpoint | Method | Permission | What it does |
|---|---|---|---|
| `/api/auth/csrf` | GET | Public | Fetch session-bound CSRF token for subsequent writes |
| `/api/auth/register` | POST JSON | Public + CSRF | Create MEMBER; requires matching `password` and `confirmPassword` |
| `/api/auth/login` | POST form-urlencoded | Public + CSRF | Check password, establish server session |
| `/api/auth/logout` | POST | Signed in + CSRF | Invalidate session |
| `/api/auth/me` | GET | Signed in | Return current persisted account, no password hash |
| `/api/auth/profile` | PUT JSON | Signed in + CSRF | Edit full name |
| `/api/auth/change-password` | PUT JSON | Signed in + CSRF | Verify current password and matching new-password confirmation, then hash it |
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

The backend integration tests verify: registration and persisted BCrypt hash; password complexity, confirmation and email normalization; rejection of duplicates/invalid fields; invalid password handling; unauthenticated access denial; MEMBER/ADMIN route separation; logout; admin role promotion; immediate account deactivation.

### Demonstration checklist (do these live)

- [ ] PostgreSQL container running (`docker compose ps`)
- [ ] Backend responds at `http://localhost:8080/api/health`
- [ ] Register a real account; confirm it persists after backend restart
- [ ] Try the same email twice; second attempt shows duplicate error
- [ ] Show `app_users.password_hash` in PostgreSQL (BCrypt hash, never plaintext)
- [ ] Enter a wrong password, then log in correctly
- [ ] Open the MEMBER dashboard; attempt ADMIN endpoint and see HTTP 403
- [ ] Change profile name and password, then verify new credentials
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
