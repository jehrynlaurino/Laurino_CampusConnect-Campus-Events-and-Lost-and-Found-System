# Increment 3 verification

## Mac run · November 27, 2026 (Philippine time)

| Check | Observed result |
| --- | --- |
| Spring Boot on JDK 21.0.10 and Maven 3.9.16 | 15 tests, 0 failures, 0 errors, 0 skipped. Twelve account-security tests use H2 in PostgreSQL compatibility mode. Three photo tests use H2 and a substituted Cloudinary adapter. |
| Production React build | Node 26.5.0, npm 11.17.0, Vite 6.4.6. Clean lockfile install succeeded. The final build transformed 55 modules. |
| Chrome 158 on Apple silicon | Passed against the built React application, Spring Boot, disposable PostgreSQL 16.16 and Mailpit 1.34.0. Five browser contexts represented an administrator, an organizer, two competing members and a visitor. |
| Persistence and recovery | Mailpit received a reset email. The emailed link reset the password. A subsequent password change worked. After restarting Spring Boot, the new password, updated profile, report and event coordinates, and cancelled event survived. The startup SQL reconciled a legacy active registration on the cancelled event. |
| Local presentation stack | Vite proxied the Spring Boot health response on port 5173. PostgreSQL and Mailpit ran natively on loopback. The separate demo database has working accounts for all three roles. |
| External services | Google Maps and Cloudinary credential fields were empty. The browser and database checks below cover coordinate persistence and provider-failure behavior. Live provider acceptance remains open. |

## Reproduce the Mac checks

Run from the repository root. Use the JDK selection from the [Mac startup guide](README.md#continue-on-macos). The test run starts and stops its own services and uses random passwords and loopback ports. It can run alongside the instructor demo. The harness does not load the private `.env`.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
brew install postgresql@16 mailpit
mvn -B -f backend/pom.xml test
npm ci --prefix frontend
npm run build --prefix frontend
python3 -m venv "${TMPDIR:-/tmp}/campusconnect-qa"
"${TMPDIR:-/tmp}/campusconnect-qa/bin/python" -m pip install playwright
"${TMPDIR:-/tmp}/campusconnect-qa/bin/python" scripts/verify_increment3.py
```

Install Chrome in Applications or set `PREVIEW_CHROME` to a Chromium-family executable. With Playwright’s own Chromium installed, the harness uses it if no executable was selected. `PREVIEW_PG_BIN` can point to a PostgreSQL 16 bin directory. The browser run uses the classpath emitted by Maven Surefire and serves `frontend/dist`. Rerun both preceding builds after changing application code.

Screenshots and local diagnostics go to the temporary directory printed at startup. To retain screenshots elsewhere, set `PREVIEW_EVIDENCE` to a fresh directory. The harness shuts down its database and deletes its data directory. Keep raw diagnostics local. The selected [Mac screenshots](docs/increment3-mac-screenshots) and [Windows screenshots](docs/increment3-screenshots) are stored separately.

### Windows reproduction

Run `mvn -B -f backend/pom.xml test`, `npm ci --prefix frontend` and `npm run build --prefix frontend`. Then, in PowerShell with Python 3, JDK 21, PostgreSQL 16 binaries and Chrome installed:

```powershell
$env:PREVIEW_PG_BIN = 'C:\Program Files\PostgreSQL\16\bin'
$env:PREVIEW_CHROME = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
py -m venv "$env:TEMP\campusconnect-qa"
& "$env:TEMP\campusconnect-qa\Scripts\python.exe" -m pip install playwright aiosmtpd
& "$env:TEMP\campusconnect-qa\Scripts\python.exe" scripts/verify_increment3.py
```

Set `JAVA_HOME` to the JDK 21 installation or ensure its `java` is on PATH. Windows uses the same workflow assertions, Windows executable suffixes and subprocess flags. Mailpit is preferred when on PATH. The original in-process SMTP receiver remains available through aiosmtpd. The revised harness was executed on macOS only.

## Approved SRS acceptance record

The requirements are in sections 3.2–3.6 and the acceptance scenarios in section 8 of the [approved SRS](docs/IT342_G01_Laurino_Jehryn_ProjectName_SRS.pdf). The rows record what the Mac checks actually exercised.

| ID | Result and evidence |
| --- | --- |
| FR-006 | Organizer saved a draft with title, description, dates, venue and a one-place capacity. A second event allowed an empty capacity. A member’s create attempt was denied. |
| FR-007 | End equal to start and end before start both returned 400. |
| FR-008 | Organizer edited and cancelled their own event. Editing and cancelling a different owner’s event returned 403. An administrator edited and cancelled an organizer-owned event. |
| FR-009 | Visitor search and detail excluded the draft. The organizer published it, and the visitor found it. |
| FR-010 | Event detail showed organizer, description, Philippine dates/times, venue and registration availability. |
| FR-011 | Organizer saw one active registration on the capacity-one event. Withdrawal reduced the count to zero. |
| FR-012 | Two public records returned in ascending start order, including a published event whose start had passed. Draft, hidden and cancelled events were excluded. |
| FR-013 | Keyword, matching category and inclusive campus-date filters selected the event. A different keyword returned no matches. |
| FR-014 | The member could register, and a repeat registration returned 409. A started event rejected registration. |
| FR-015 | Two authenticated members requested the last place concurrently. One response was 200 and the other 409. The organizer count was one. |
| FR-016 | Before the start, the member cancelled and registered again. Withdrawal after the start returned 409. |
| FR-017 | The member’s registration list showed ACTIVE after registration and CANCELLED after event cancellation. The legacy-row reconciliation also restored the active count to zero at restart. |
| FR-018 | The browser saved a FOUND report, and a second member created a LOST report with the required fields. |
| FR-019 | Removing title, type, category, description, incident time or location rejected the request. Future incident time was also rejected. |
| FR-020 | Empty reports were saved. The photo contract test decoded JPEG, PNG and WebP, accepted three, and rejected a fourth. The browser/API rejected invalid content and an upload over 5 MiB. The decoder also limits images to 24 megapixels. Successful live upload is pending. |
| FR-021 | An OPEN report owner saved an edit. Another owner was denied. Editing a resolved report was rejected. The administrator changed report status. |
| FR-022 | Keyword, type, category and OPEN/RESOLVED status filters returned matching reports. Hidden and closed reports disappeared from public search. |
| FR-023 | Detail showed the description, venue, incident date/time and current status. The adapter test verified saved image references. Displaying a live Cloudinary image in the browser is pending. |
| FR-024 | Both the owner and administrator resolved reports. A different member’s attempt returned 403. |
| FR-025 | An event and report accepted coordinate pairs entered in the form, saved and reopened with their location names. Missing locations and out-of-bounds pairs were rejected. Clicking a live campus map and confirming the institution’s rectangle remain pending. |
| FR-026 | Google Maps and Advanced Marker code uses the saved position and the record’s title and location. Live rendering of both event and report markers remains pending. |
| FR-027 | The adapter test verified the stored HTTPS URL and public ID, removal from the report, and queued provider deletion. A real Cloudinary round trip remains pending. |
| FR-028 | Chrome blocked the Maps script for a saved pin and still displayed the written location. With storage unconfigured, the upload showed a retry action, retried and left zero image records. |
| FR-031 | An administrator hid and restored the event, hid and closed the report, and changed visibility without reopening the closed report. Visitors lost access. A member’s moderation attempt was denied. |

Chrome also checked the list/create screens at 360, 768 and 1366 px, horizontal overflow and keyboard focus from title to category. The additional implemented requirements are FR-011, FR-016, FR-017, FR-024 and FR-028. Optional claims (FR-029, FR-030, FR-032) and related-report suggestions (FR-033) remain deferred.

### Increment 1 and 2 regression

The 12 authentication tests cover FR-001 through FR-005. They check registration and password hashing, duplicate rejection, login/logout, role boundaries, administrator promotion and deactivation, profile persistence and password changes. They also cover generic recovery responses, rate limits, token expiry/replacement/single use and invalidation of existing sessions. Chrome separately exercised account registration, all three dashboards, profile update, real SMTP reset delivery to Mailpit, sign-in with a changed password and logout. The earlier [Increment 2 report](docs/Increment-2-Completion-Report.pdf) retains its original evidence.

## Live provider acceptance

After the owner has configured the private values in the [provider setup guide](README.md#provider-configuration) and approved any potential charges, restart Spring Boot. Visit `http://localhost:5173` so the browser origin matches the website restriction.

1. As organizer, enter a venue, click an in-bounds point on the event map, save and publish. Open the public detail in a fresh browser and check the displayed marker, title/location and saved coordinates. Repeat on an OPEN report. Reopen each edit form and confirm the pin can be moved, saved and reopened.
2. As the report owner, select a real JPG, PNG or WebP within the documented limits. Choose **Upload photo**. Open the report as a visitor and reload it. Confirm that the image loads fully and matches the selected file. In PostgreSQL, inspect just the image metadata with `SELECT report_id, public_id, secure_url FROM report_image;`.
3. Remove the photo as the owner. Refresh the visitor’s report and verify the image and database row disappear. The cleanup worker runs every minute. Confirm deletion in the Cloudinary product environment after cleanup and check the matching cleanup row has cleared. Record only sanitized UI evidence.

## Earlier Windows evidence · November 26, 2026

At checkpoint `bbbbaa440190784a2185ee1503cd91533c125e1d`, Windows recorded 15 passing Maven tests, a successful React build and a Chrome run using PostgreSQL 16 and a local SMTP receiver. It covered the one-place race, report ownership, moderation, invalid/failed photo uploads, reset email, restart persistence and small screens. These original screenshots were retained for the completion report. Live Google Maps and Cloudinary success were outstanding at that checkpoint.
