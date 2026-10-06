# Library Management System

A full-stack college library application built with Java 17, Spring Boot 3.5, Spring Data JPA, H2/MySQL, and a responsive browser UI.

## Features

- Book catalog and member management, search, category filters, pagination, and cover images.
- Loans, renewals, returns, configurable late fees, and fine-payment audit details.
- Reservations and a first-in/first-out waitlist. A returned copy is held for the next member for 48 hours.
- QR and Code 128 book labels, student-ID QR labels, and a camera/manual **Scan & Issue** workflow.
- Transaction history, dashboard, overdue and CSV reports, CSV book import, and client-side catalog/member exports.
- Optional SMTP due-date reminders.
- Local durable H2 storage or a MySQL profile; multi-stage Docker image and Compose setup.
- Admin/librarian login, with destructive catalog/member operations restricted to admins.

## Run locally

Requirements: JDK 17 and Maven 3.9+.

```powershell
mvn spring-boot:run
```

Or double-click `run.bat`. Open:

- Dashboard: http://localhost:8081
- H2 console (development only): http://localhost:8081/h2-console
- Swagger UI (development only): http://localhost:8081/swagger-ui/index.html

The default database is a **file-backed H2 database** under `data/`; data remains after restarts. The H2 console uses JDBC URL `jdbc:h2:file:./data/librarydb`, username `sa`, and a blank password. Keep the console on trusted local development machines only.

### Demo login

- Admin: `admin` / `admin123`
- Librarian: `librarian` / `library123`

These credentials are for local demonstration only. Change them before sharing the application or exposing it beyond localhost. Credentials are configured with `LIBRARY_ADMIN_USERNAME`, `LIBRARY_ADMIN_PASSWORD`, `LIBRARY_LIBRARIAN_USERNAME`, and `LIBRARY_LIBRARIAN_PASSWORD`. HTTP Basic credentials are sent with API requests; use HTTPS via a trusted reverse proxy for any network deployment.

## MySQL / Docker Compose

1. Copy `.env.example` to `.env`.
2. Set strong unique values for the application and database passwords. Do not commit `.env`.
3. Start the application and MySQL:

```powershell
docker compose up --build
```

Open http://localhost:8081. Compose enables the `mysql,prod` profiles, disables the H2 console and demo seeding, waits for MySQL health, and persists database files in a named Docker volume. For a non-containerized MySQL database, configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, then start with `SPRING_PROFILES_ACTIVE=mysql,prod`.

## Email due-date reminders

Reminders are off by default. Configure SMTP using Spring Mail environment variables (for example `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, and `SPRING_MAIL_PASSWORD`), set `LIBRARY_REMINDERS_FROM`, then set `LIBRARY_REMINDERS_ENABLED=true`. The scheduler sends reminders for loans due within three days once per loan per day (cron defaults to 08:00). A librarian can trigger a run using `POST /api/notifications/due-reminders`. Email delivery requires a reachable SMTP server; keep SMTP credentials in environment variables or a secret manager.

## QR / barcode workflow

- Each book receives an ID such as `BK00001`. Its QR payload contains that catalog ID; the Code 128 barcode encodes the same value.
- Each student's QR payload is the member's unique membership number (for example `23CSE104`).
- Print book QR/barcode images from the Books page and student QR images from the Members page.
- In **Scan & Issue**, scan/enter the student's ID first, then the book ID. Review the identified student/book and press **Confirm issue**; the due date and issue record come from the server.
- For a return, select **Return book**, scan/enter the book ID, choose the active borrower if there is more than one, and confirm. The server records the return and calculates the configured fine.
- Camera scanning uses the browser's native `BarcodeDetector` API and requires camera permission on localhost or HTTPS. Manual entry supports the catalog ID/ISBN and member ID when a camera/API is unavailable.

## CSV and reports

- Import books on the Books page using headers: `title,author,isbn,category,publicationYear,totalCopies`; `publisher` is optional. The import response reports imported rows and row-level errors. Imports are not all-or-nothing: valid rows are saved and invalid rows are reported.
- Reports are available from the UI and API: `GET /api/reports/books.csv`, `/api/reports/members.csv`, `/api/reports/transactions.csv`, `/api/reports/overdue.csv`.
- Client-side exports remain available for the current catalog and member lists.

## Backup and recovery

For local H2, run:

```powershell
.\scripts\backup-library.ps1
```

This creates an SQL snapshot under `backups/`. Restore from a trusted snapshot with:

```powershell
.\scripts\backup-library.ps1 -RestorePath .\backups\library-20261006-120000.sql
```

For Docker MySQL, create backups with `docker compose exec -T db mysqldump -u root -p"$env:MYSQL_ROOT_PASSWORD" library_db > .\backups\library.sql`; restore with `Get-Content .\backups\library.sql | docker compose exec -T db mysql -u root -p"$env:MYSQL_ROOT_PASSWORD" library_db`. Protect backups because they contain personal member data.

## Useful API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/books` | List the catalog |
| `GET` | `/api/books/lookup?code=BK00001` | Resolve a book ID, barcode, or ISBN |
| `GET` | `/api/books/{id}/qr.png` | Render a book QR code |
| `GET` | `/api/books/{id}/barcode.png` | Render a Code 128 barcode |
| `GET` | `/api/members/lookup?code=23CSE104` | Resolve a student/membership ID |
| `GET` | `/api/members/{id}/qr.png` | Render a student ID QR code |
| `POST` | `/api/transactions/issue` | Issue a loan |
| `POST` | `/api/transactions/return` | Return a loan and calculate its fine |
| `POST` | `/api/reservations` | Add a member to a book waitlist |
| `GET` | `/api/dashboard/stats` | Dashboard metrics and fine totals |
| `POST` | `/api/notifications/due-reminders` | Send configured due-date reminders |

All `/api/**` endpoints require an admin or librarian account. DELETE operations and member status changes require the admin role.

## Configuration and tests

Loan duration, renewal limits, and daily fine rate can be set with `LIBRARY_LOAN_DEFAULT_DAYS`, `LIBRARY_LOAN_MAX_DAYS`, `LIBRARY_LOAN_MAX_RENEWALS`, and `LIBRARY_FINE_DAILY_RATE`.

```powershell
mvn test
```

Tests use an isolated in-memory H2 database and do not alter the local `data/` database.
