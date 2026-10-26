# Smart Hospital frontend

React and Vite frontend for the existing Spring Boot API in `../backend/demo`. It does not seed or use sample patient data: clinical lists and records come from the API.

## Setup

Requirements: Node.js/npm and the Spring Boot backend with PostgreSQL configured.

```powershell
if (!(Test-Path .env.local)) { Copy-Item .env.example .env.local }
npm install
npm run dev
```

The default API base URL is `http://localhost:8080`. Set `VITE_API_BASE_URL` in `.env.local` to the backend origin when it differs; do not add a trailing API path. The backend's `FRONTEND_ORIGIN` must allow the Vite origin (default `http://localhost:5173`) for CORS.

Set `VITE_HOSPITAL_TIME_ZONE` in `.env.local` to the same IANA zone ID as the backend's `HOSPITAL_TIME_ZONE`. The frontend uses this hospital timezone for appointment calendar-date status labels; it defaults to `Asia/Kolkata`.

## Start the backend locally

Start PostgreSQL and make sure the `smart_hospital` database exists. In a PowerShell terminal, set your local database credentials (do not commit them or put them in frontend environment files), then run the Spring Boot application:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/smart_hospital"
$env:DB_USERNAME = "<your-local-PostgreSQL-username>"
$env:DB_PASSWORD = "<your-local-PostgreSQL-password>"
$env:FRONTEND_ORIGIN = "http://localhost:5173"
Set-Location ..\backend\demo
.\mvnw.cmd spring-boot:run
```

Doctor availability is interpreted in `HOSPITAL_TIME_ZONE` (an IANA zone ID); it defaults to `Asia/Kolkata` and is independent of the backend machine's timezone.

Run the frontend from a second terminal with `npm run dev`, and open the printed Vite URL. Verify the backend is listening with:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/health/database
```

The health endpoint is public and should return `UP` when the API can connect to the database. If the browser still reports a connection error, check that the backend is listening on the configured URL and that the browser's frontend origin exactly matches `FRONTEND_ORIGIN`. If `VITE_API_BASE_URL` was changed, restart Vite so it reads the updated environment.

For interactive API testing, open `http://localhost:8080/swagger-ui/index.html` after starting the backend. The documentation page requires HTTP Basic authentication. On a database with no active administrator, set up a local developer account in the backend PowerShell terminal before startup:

```powershell
$env:BOOTSTRAP_ADMIN_EMAIL = "copilot"
$adminPassword = Read-Host "Choose a private admin password (12-72 characters)" -AsSecureString
$env:BOOTSTRAP_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new("", $adminPassword).Password
.\mvnw.cmd spring-boot:run
```

Enter username `copilot` and the password you chose in the Swagger UI authentication prompt. Bootstrap does not reset passwords or create another admin if an active administrator already exists. No default password is supplied; choose one privately and do not commit it.

Useful commands:

```powershell
npm run build
npm run lint
```

## Sign-in and privacy

Sign in uses the backend's stateless HTTP Basic authentication and validates the credentials with `GET /api/v1/auth/me`. The Basic authorization value is held only in React memory, is sent over `fetch`, and is not written to browser storage or logged. Refreshing the page signs the user out. Patient self-registration uses the existing `POST /api/v1/patients` route; staff accounts must be created by an administrator.

Navigation is tailored to the five backend roles (`ADMIN`, `DOCTOR`, `RECEPTIONIST`, `LAB_TECHNICIAN`, `PATIENT`) for usability. This is not an authorization boundary: protected API requests are still authorized by Spring Security and service-level ownership checks.

## Implemented workspaces

- Dashboard with database health and accessible appointment summary.
- Patient registration, search, updates and doctor assignment for roles allowed by the backend.
- Doctor directory and availability management.
- Appointment scheduling, rescheduling and cancellation.
- Clinical encounter listing, creation, draft updates and finalization.
- Encounter-scoped prescriptions, editable draft items, finalization and clinician history.
- Lab technician worklist, result version entry, encounter order/result views and clinician verification.
- AI-assisted report generation, draft editing, submission, clinician approval/rejection and history review. Generation remains a backend action; reports require explicit clinician approval.
- Shared loading/error/success feedback, forms, tables, status badges, dialogs and responsive layouts.

## API boundaries and limitations

The UI follows the backend controller permissions and DTO fields. Some operations consequently use record IDs rather than inventing list APIs:

- Patient search is only exposed by the backend to `ADMIN` and `RECEPTIONIST`; the patient role has no current-profile endpoint. Patient appointments and encounters use backend-scoped searches; patient records are not listed for patients.
- There is no `GET /api/v1/patients/me` endpoint, and `GET /api/v1/patients` is not permitted for `DOCTOR` or `PATIENT`. Patients can update their own record only when its ID is known; clinicians cannot search the patient directory.
- Appointment/encounter creation requires backend patient/doctor identifiers. Admin/receptionist appointment booking selects from the existing directories, but doctors must enter a patient ID because no clinician-scoped patient-search endpoint exists.
- There are no cross-encounter prescription, lab, or report listing endpoints. The existing routes are scoped to an encounter or record ID, so users enter an encounter ID to access them. Lab result verification uses the actual result history and requires an explicit clinician confirmation.
- The API documentation at `../backend/demo/API.md` covers encounters, prescriptions, laboratory and reports. Authentication, patient, doctor, appointment and database-health contracts are defined by the existing controllers and DTOs.

No incompatible endpoint was found for the implemented flows; the UI calls the existing routes and lets the backend enforce permissions, assignment, scheduling conflicts and state transitions.

The backend also states that encounter changes do not have a separate audit-history module. Report and prescription history endpoints are available and used by the frontend. No clinical recommendations, generated measurements, automatic prescriptions, or automatic report approvals are implemented in the frontend.
