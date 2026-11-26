"""Populate the instructor-provided Increment 2 report template with verified project facts."""

from pathlib import Path
from html import escape
from base64 import b64encode
import sys

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Pt
from docx.oxml.table import CT_Tbl
from docx.oxml.text.paragraph import CT_P
from docx.table import Table
from docx.text.paragraph import Paragraph


DOCS = Path(__file__).parent
TEMPLATE = DOCS / "IT342-PD-Completion-Report-Template.docx"
OUTPUT = DOCS / "Increment-2-Completion-Report.docx"
REPOSITORY = "https://github.com/jehrynlaurino/Laurino_CampusConnect-Campus-Events-and-Lost-and-Found-System"
SCREENSHOTS = [
    ("01-profile-saved.png", "The member saves a new full name. It also persisted after a browser refresh."),
    ("02-password-validation.png", "The change-password form flags a confirmation that differs from the new password."),
    ("03-password-changed.png", "After the password change, CampusConnect asks the member to sign in again."),
    ("04-reset-request.png", "The recovery form acknowledges the request with its generic response."),
    ("05-received-email.png", "The local SMTP receiver captured the reset email. The transcript hides the reset URL."),
    ("06-reset-form.png", "Following the emailed link opens the form for entering and confirming a new password."),
    ("07-password-reset.png", "The reset succeeds and the member returns to the sign-in page."),
    ("08-used-link.png", "Submitting the same reset link again produces an error."),
    ("09-dashboard.png", "Signing in with the recovered password opens the member dashboard."),
]


def replace_paragraph(paragraph, text):
    paragraph.clear()
    paragraph.add_run(text)


def replace_cell(cell, text):
    paragraph = cell.paragraphs[0]
    paragraph.clear()
    paragraph.add_run(text)
    for extra in cell.paragraphs[1:]:
        extra._element.getparent().remove(extra._element)


def fill_row(table, row, values):
    for cell, value in zip(table.rows[row].cells, values):
        replace_cell(cell, value)


def remove_paragraph(paragraph):
    paragraph._element.getparent().remove(paragraph._element)


doc = Document(TEMPLATE)
paragraphs = list(doc.paragraphs)

# Keep the official headings/section order, while removing only the template's gray instructions.
replace_paragraph(paragraphs[0], "IT342 – Systems Integration and Architecture 1")
replace_paragraph(paragraphs[1], "Project Development 2")
replace_paragraph(paragraphs[2], "Implementation / Completion Report")
replace_paragraph(
    paragraphs[7],
    "CampusConnect is a centralized web application for students, faculty, staff, authorized event organizers, and campus administrators. It provides the secure account foundation for future campus-event and lost-and-found workflows; this increment delivers profile management and account-password security.",
)
replace_paragraph(paragraphs[8], "Core feature for this increment: Account Security – Forgot/Reset Password and Change Password")
replace_paragraph(
    paragraphs[10],
    "Secure account recovery and password change are core to safe access for every CampusConnect role. They protect the existing authenticated dashboards while allowing a user to regain access without exposing account existence or storing a plaintext reset token.",
)
replace_paragraph(paragraphs[14], "2.2 Core Feature: Account Security – Forgot/Reset Password and Change Password")
replace_paragraph(
    paragraphs[15],
    "Users can request a reset link from the login screen, receive a real SMTP email when configured, and set a new strong password using a one-time 15-minute token. Authenticated users can also change their password after verification of the current password; both flows end every active session for the account.",
)
replace_paragraph(paragraphs[19], "1. A user selects Forgot password? on Login, submits an email, and receives the same generic response whether or not the account exists.")
replace_paragraph(paragraphs[20], "2. For a real active account, Spring Mail delivers a CampusConnect reset link built from the trusted FRONTEND_URL; the user opens it within 15 minutes.")
replace_paragraph(paragraphs[21], "3. The user submits matching strong passwords. The backend atomically consumes the token, stores a BCrypt password hash, invalidates sessions, and returns the user to Login.")
replace_paragraph(
    paragraphs[30],
    "Figures 1–9 follow a disposable member account in the running React and Spring Boot application. This browser session used an isolated H2 test database and a local SMTP receiver. Verification with PostgreSQL and Mailpit remains pending.",
)
replace_paragraph(
    paragraphs[31],
    "The Spring Boot integration suite passed all 12 tests on November 26, 2026. The React production build also passed. TESTING.md records the browser checks and the remaining Docker verification.",
)
section = doc.sections[0]
width = section.page_width - section.left_margin - section.right_margin
for number, (filename, description) in enumerate(SCREENSHOTS, 1):
    caption = f"Figure {number}. {description}"
    figure = paragraphs[31].insert_paragraph_before()
    figure.alignment = WD_ALIGN_PARAGRAPH.CENTER
    figure.paragraph_format.keep_together = True
    figure.paragraph_format.space_after = Pt(10)
    picture = figure.add_run().add_picture(str(DOCS / "screenshots" / filename), width=width)
    picture._inline.docPr.set("descr", description)
    figure.add_run().add_break()
    figure.add_run(caption).italic = True
replace_paragraph(paragraphs[40], "Name: Jehryn Laurino")
replace_paragraph(paragraphs[41], "Date: 11/26/2026")

# Remove only gray instruction/placeholder-note paragraphs. Official content sections remain in place.
for index in [3, 6, 9, 13, 18, 27, 29, 33, 35, 37]:
    remove_paragraph(paragraphs[index])

# Project information.
fill_row(doc.tables[0], 0, ["System Name", "CampusConnect: Campus Events and Lost-and-Found System"])
fill_row(doc.tables[0], 1, ["Student Name", "Laurino, Jehryn"])
fill_row(doc.tables[0], 2, ["Section", "G01"])
fill_row(doc.tables[0], 3, ["Increment", "Project Development 2 – Account Security (Vertical Slice)"])
fill_row(doc.tables[0], 4, ["GitHub Repository", REPOSITORY])
fill_row(doc.tables[0], 5, ["Date Submitted", "11/26/2026"])

# Standard features.
fill_row(doc.tables[1], 1, ["Account Registration", "Complete", "Increment 1 regression test passed; public registration creates MEMBER accounts with BCrypt passwords."])
fill_row(doc.tables[1], 2, ["Login / Logout", "Complete", "Increment 1 regression test passed; server-side sessions, CSRF protection, and logout remain enforced."])
fill_row(doc.tables[1], 3, ["Role-Based User Dashboard", "Complete", "MEMBER, ORGANIZER, and ADMIN backend authorization regression tests passed."])
fill_row(doc.tables[1], 4, ["User Profile (view and edit)", "Partial", "A member saved a name change and refreshed the page in the H2 browser run (Figure 1). Persistence across a PostgreSQL restart still needs verification."])

# Role and flow details.
fill_row(doc.tables[2], 1, ["MEMBER / ORGANIZER / ADMIN", "Authenticated users view and update only their own profile and change only their own password. Existing sessions end after a password update."])
fill_row(doc.tables[2], 2, ["Unauthenticated visitor", "May request a reset and use a valid received reset link. The generic recovery response does not disclose whether an email is registered."])

# CRUD description.
fill_row(doc.tables[3], 1, ["Create", "Yes", "Forgot Password form", "Any visitor with an email address; a token is created only for an active matching account"])
fill_row(doc.tables[3], 2, ["Read", "Yes", "My Account", "Authenticated MEMBER, ORGANIZER, ADMIN — own safe profile only"])
fill_row(doc.tables[3], 3, ["Update", "Yes", "My Account, Change Password, Reset Password", "Profile owner only; password update via verified session or valid reset token"])
fill_row(doc.tables[3], 4, ["Delete", "No", "N/A", "Tokens are marked used/invalidated for security traceability instead of being deleted by a user"])

# Database models.
fill_row(doc.tables[4], 1, ["app_users (AppUser)", "id, full_name, email, password_hash, role, active, created_at, credential_version", "One user to many password_reset_tokens"])
fill_row(doc.tables[4], 2, ["password_reset_tokens (PasswordResetToken)", "id, user_id, token_hash, created_at, expires_at, used_at", "user_id foreign key to app_users; unique token_hash; user and expiry indexes"])

# Key files (the template uses Django terms, adapted to the Java/React stack).
fill_row(doc.tables[5], 1, ["Models", "backend/src/main/java/com/campusconnect/user/AppUser.java; backend/src/main/java/com/campusconnect/auth/PasswordResetToken.java"])
fill_row(doc.tables[5], 2, ["Views / services", "backend/src/main/java/com/campusconnect/auth/AuthController.java; AccountService.java; PasswordResetService.java; PasswordResetEmailService.java"])
fill_row(doc.tables[5], 3, ["Routes / security", "backend/src/main/java/com/campusconnect/config/SecurityConfig.java; DatabaseAccountFilter.java; frontend/src/App.jsx"])
fill_row(doc.tables[5], 4, ["Forms", "frontend/src/pages/AuthPages.jsx; ChangePasswordPage.jsx; AccountPage.jsx"])
fill_row(doc.tables[5], 5, ["Templates / UI", "frontend/src/pages/AuthPages.jsx; frontend/src/pages/AccountPage.jsx; frontend/src/pages/ChangePasswordPage.jsx"])

# Validation/business rules.
fill_row(doc.tables[6], 1, ["Profile and password updates must belong to the signed-in user", "Authenticated identity is read from Spring Security; profile DTO contains only fullName; backend authorization and database account filter run on requests."])
fill_row(doc.tables[6], 2, ["Reset is time-limited, one-time, non-enumerating, and rate-limited", "256-bit SecureRandom token is SHA-256 hashed at rest; 15-minute expiry, atomic consume update, generic response, replacement invalidation, and 3/5 attempt windows."])

# Branch/PR history: do not invent branches or pull requests.
fill_row(doc.tables[7], 1, ["main", "main", "N/A — individual repository direct commit", "Increment 2 profile, password-recovery, session-security, test, documentation, and report changes"])
fill_row(doc.tables[7], 2, ["N/A", "N/A", "N/A", "No feature branch or pull request was created"])
fill_row(doc.tables[7], 3, ["main", "main", "N/A", "Increment 2 release commit including profile, password security, tests, documentation, and report"])

# Remaining work and limitations.
fill_row(doc.tables[8], 1, ["1", "Live Docker verification", "Run the PostgreSQL and Mailpit containers, complete the browser flow, and confirm actual email delivery on a permitted computer.", "High", "Project Development 2 evidence"])
fill_row(doc.tables[8], 2, ["2", "Verify PostgreSQL persistence", "Restart the backend against the same PostgreSQL database and confirm the updated profile name remains available.", "High", "Project Development 2 evidence"])
fill_row(doc.tables[8], 3, ["3", "Campus events and lost-and-found modules", "Implement the remaining approved SRS modules in later increments.", "Medium", "Later increment"])

fill_row(doc.tables[9], 1, ["ISS-01", "Live PostgreSQL/Mailpit delivery was not verified on the school-managed computer.", "Docker Desktop requires administrator approval; no local Docker/PostgreSQL runtime was available.", "Major", "Open — configuration and automated H2 integration tests are complete; run the documented live checklist on a permitted machine."])
fill_row(doc.tables[9], 2, ["ISS-02", "Browser screenshot evidence", "Follow the account settings and recovery flow in Figures 1–9.", "Minor", "Resolved. The nine captures cover the H2 browser run and locally received reset email."])

# Do not invent credentials. No live database was started for this update.
fill_row(doc.tables[10], 1, ["ADMIN", "Not provided — no live PostgreSQL database was started", "Not provided"])
fill_row(doc.tables[10], 2, ["MEMBER / ORGANIZER", "Not provided — create through the real UI for the live demonstration", "Not provided"])

doc.core_properties.title = "CampusConnect Increment 2 Completion Report"
doc.core_properties.subject = "IT342 Project Development 2"
doc.core_properties.author = "Jehryn Laurino"
doc.save(OUTPUT)

# Word's PDF automation can be unavailable on managed computers. This optional
# print representation retains the completed official structure and table order
# so Edge can produce the required PDF without altering the DOCX template.
if "--html" in sys.argv:
    blocks = []
    for child in doc.element.body.iterchildren():
        if isinstance(child, CT_P):
            text = Paragraph(child, doc).text.strip()
            images = child.xpath(".//a:blip/@r:embed")
            if images:
                data = b64encode(doc.part.related_parts[images[0]].blob).decode("ascii")
                blocks.append(f'<figure><img src="data:image/png;base64,{data}" alt="{escape(text)}"><figcaption>{escape(text)}</figcaption></figure>')
                continue
            if not text:
                continue
            if text[0].isdigit() and (text[1:2] == "." or text.startswith("2.1") or text.startswith("2.2")):
                tag = "h2"
            elif text in {"Project Information", "User Roles and Access", "User Flow", "CRUD Operations", "Database Models", "Key Files", "Validation and Business Rules", "Branches and Pull Requests", "Screenshots"}:
                tag = "h3"
            elif text.startswith("Core feature for") or text.startswith("Description:"):
                tag = "h3"
            else:
                tag = "p"
            blocks.append(f"<{tag}>{escape(text)}</{tag}>")
        elif isinstance(child, CT_Tbl):
            table = Table(child, doc)
            rows = []
            for row_number, row in enumerate(table.rows):
                cells = []
                cell_tag = "th" if row_number == 0 else "td"
                for cell in row.cells:
                    cells.append(f"<{cell_tag}>{escape(cell.text).replace(chr(10), '<br>')}</{cell_tag}>")
                rows.append("<tr>" + "".join(cells) + "</tr>")
            blocks.append("<table>" + "".join(rows) + "</table>")
    html = """<!doctype html><html><head><meta charset=\"utf-8\"><style>
      @page { size: A4; margin: 14mm; }
      body { font-family: Calibri, Arial, sans-serif; color:#1f2933; font-size:9.5pt; line-height:1.35; }
      h1 { text-align:center; font-size:17pt; color:#17365d; margin:0 0 4pt; }
      h2 { font-size:13pt; color:#17365d; border-bottom:1px solid #9fbad0; margin:18pt 0 7pt; padding-bottom:2pt; }
      h3 { font-size:10.5pt; color:#17365d; margin:12pt 0 4pt; }
      p { margin:4pt 0 7pt; }
      figure { width: 164mm; max-width:100%; margin: 4mm auto; break-inside:avoid; }
      figure img { width:100%; height:auto; display:block; border:0.2mm solid #cfdbdf; box-sizing:border-box; }
      figcaption { font-size:10pt; line-height:1.3; margin-top:1.5mm; }
      table { border-collapse:collapse; width:100%; margin:5pt 0 12pt; page-break-inside:avoid; }
      th { background:#d9eaf7; font-weight:bold; }
      th, td { border:1px solid #7890a5; vertical-align:top; padding:4pt; text-align:left; word-break:break-word; }
    </style></head><body>""" + "\n".join(blocks) + "</body></html>"
    (DOCS / "_increment2_report_print.html").write_text(html, encoding="utf-8")

print(OUTPUT)
