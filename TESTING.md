# Increment 1 validation evidence

Before submission, capture your own screenshots of these *running* checks:

1. Registration form showing required field validation.
2. Duplicate email showing conflict message; new user visible in PostgreSQL.
3. Database `password_hash` beginning with BCrypt marker (e.g., `$2a$`), not the entered password.
4. Login form with incorrect password error and successful login redirect.
5. Member dashboard with MEMBER status confirmed from backend.
6. Browser DevTools: GET `/api/dashboard/admin` returns 403 for MEMBER (do not show private data).
7. Administrator dashboard and user list; promote MEMBER to ORGANIZER.
8. Organizer logs in and sees Organizer dashboard.
9. Logout then protected resource returns 401.
10. `mvn test` results and `npm run build` output.
11. GitHub repo showing latest pushed commit before the cutoff.

Use realistic personal or synthetic test accounts that *you registered through the UI*. Do not seed member accounts directly or substitute mock responses for integration tests.
