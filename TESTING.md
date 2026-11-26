# Increment 2 testing and validation evidence

## Automated checks actually run

On **November 26, 2026**, the following checks were executed from this repository:

| Check | Result | Scope |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | Passed: **12 tests, 0 failures, 0 errors** | Spring Boot integration suite using a clean H2 database in PostgreSQL compatibility mode. |
| Password-reset session regression | Passed | A test first exposed an old session returning a misleading successful expired-session response. The security configuration was corrected to return `401`, and the focused test passed. |
| `npm run build` | Passed: Vite built 49 modules successfully | React production bundle completed in 510 ms. |

The backend integration tests verify:

- Registration, duplicate prevention, input validation, BCrypt password hashing, email normalization, login failure, login/logout, and protected endpoint/RBAC behaviour from Increment 1.
- Admin promotion/deactivation and continued backend role enforcement.
- Authenticated user profile update persistence and safe `/api/auth/me` response without a password hash.
- Change-password validation, old-password rejection, new-password authentication, and invalidation of another active session.
- Generic forgot-password responses for unknown accounts, recovery-request rate limiting, and real Spring Mail message construction through a mocked mail transport.
- SHA-256-only reset-token storage, 15-minute expiry rejection, replacement-token invalidation, atomic single use, reset-attempt rate limiting, old-password rejection, and existing-session invalidation after reset.

## Live Docker/PostgreSQL/Mailpit checklist

These checks are intentionally not marked as complete because Docker Desktop and a PostgreSQL runtime were unavailable on the school-managed computer. Run them with `docker compose --env-file .env up -d` before a live demonstration, then capture only real screenshots:

1. `docker compose ps` shows both `campusconnect-db` and `campusconnect-mailpit` healthy/running.
2. Start the backend and confirm `http://localhost:8080/api/health` responds.
3. Register an account through the UI and refresh/restart the backend; confirm the account and profile-name update persist in PostgreSQL.
4. Open **My Account** as MEMBER, ORGANIZER, and ADMIN; confirm only the signed-in profile is shown and role/email cannot be edited there.
5. Request a reset for a registered address. Open the actual message in Mailpit at `http://localhost:8025`; verify the reset link, 15-minute wording, and ignore instruction.
6. Request reset for an unknown address; capture the same generic UI message, without exposing account existence.
7. Use the actual reset link once; confirm old password fails, new password logs in, current/other sessions are rejected, reused and expired links fail, and a newer request invalidates the older link.
8. Change password while signed in; verify wrong current password, weak password, same password, and mismatched confirmation are rejected. Verify the old password and an older session no longer work after success.
9. Recheck Increment 1 registration, login/logout, MEMBER/ORGANIZER/ADMIN dashboards, admin role promotion, deactivation, and API protection.

## Recommended live screenshot evidence

Capture your own screenshots of these *running* checks:

1. Registration form showing required field validation.
2. Duplicate email showing conflict message; new user visible in PostgreSQL.
3. Database `password_hash` beginning with BCrypt marker (e.g., `$2a$`), not the entered password.
4. Login form with incorrect password error and successful login redirect.
5. Member dashboard with MEMBER status confirmed from backend.
6. Browser DevTools: GET `/api/dashboard/admin` returns 403 for MEMBER (do not show private data).
7. Administrator dashboard and user list; promote MEMBER to ORGANIZER.
8. Organizer logs in and sees Organizer dashboard.
9. Logout then protected resource returns 401.
10. Mailpit message containing a reset link (do not expose the token in a public report screenshot).
11. Reset form success and rejected reused/expired-link message.
12. Change-password screen and a subsequent sign-in with the new password.
13. `mvn test` results and `npm run build` output.
14. GitHub repo showing the latest pushed commit.

Use realistic personal or synthetic test accounts that *you registered through the UI*. Do not seed member accounts directly or substitute mock responses for integration tests.
