# Clinical Encounter API

Base path: `/api/v1/encounters`

All routes require HTTP Basic authentication. `ADMIN`, `DOCTOR`, and `PATIENT` may read encounter routes. `RECEPTIONIST` and `LAB_TECHNICIAN` are denied access. Only `DOCTOR` may create or update encounters, and the doctor must currently be assigned to the patient. Patients may read only their own encounters; doctors may read encounters for their currently assigned patients; admins may read all encounters. Finalized encounters cannot be updated.

## Interactive API documentation

When the backend is running, open `http://localhost:8080/swagger-ui/index.html`. The Swagger UI page and OpenAPI document require HTTP Basic authentication. Sign in with an existing account; use Swagger UI's **Authorize** control to authenticate API requests with that account. Endpoint authorization and role restrictions are still enforced by Spring Security.

For a new local development database, an administrator can be bootstrapped by setting `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` before starting the backend. In PowerShell, choose a developer email and enter a private password at the hidden prompt:

```powershell
$env:BOOTSTRAP_ADMIN_EMAIL = "copilot"
$adminPassword = Read-Host "Choose a private admin password (12-72 characters)" -AsSecureString
$env:BOOTSTRAP_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new("", $adminPassword).Password
.\mvnw.cmd spring-boot:run
```

Use `copilot` and the password you entered at the Swagger UI Basic Authentication prompt. Bootstrap only creates an admin when no active admin account exists. If one already exists, sign in using that account; these settings do not reset or replace its password. Do not commit or put the password in frontend configuration.

## Create

`POST /api/v1/encounters` returns `201 Created`. The authenticated doctor is taken from the security principal; clients cannot choose a doctor or author.

```json
{
  "patientId": 12,
  "appointmentId": 34,
  "encounterDate": "2026-10-20",
  "clinicalNotes": "History and examination notes",
  "diagnosis": "Assessment"
}
```

`appointmentId` is optional. When supplied, it must reference a non-cancelled appointment for the same patient and doctor on the encounter date. The initial status is `DRAFT`.

## Retrieve

`GET /api/v1/encounters/{id}` returns `200 OK` for an authorized reader and `404 Not Found` for an unknown encounter.

## Search

`GET /api/v1/encounters` returns a paginated response (`200 OK`). Optional query parameters are `status` (`DRAFT` or `FINALIZED`), `fromDate`, `toDate` (ISO dates, inclusive), `patientId`, `doctorId`, `page` (default `0`), and `size` (default `20`, maximum `100`). Patient and doctor scopes are imposed by the authenticated identity and cannot be widened with query parameters.

## Update

`PUT /api/v1/encounters/{id}` returns `200 OK`. Only the doctor who authored the current assigned-patient encounter can update a draft. Setting `status` to `FINALIZED` finalizes it; finalized records are immutable.

```json
{
  "encounterDate": "2026-10-20",
  "clinicalNotes": "Reviewed and updated notes",
  "diagnosis": "Updated assessment",
  "status": "FINALIZED"
}
```

Clinical notes are limited to 50,000 characters; diagnosis is limited to 2,000. Encounter dates cannot be in the future. Invalid requests return `400 Bad Request`, role/ownership violations return `403 Forbidden`, and attempts to modify a finalized record or use a cancelled appointment return `409 Conflict`.

No audit-history module exists in the backend yet, so encounter changes are not currently written to a separate audit log. The encounter row records its author and creation/update timestamps.

# Prescriptions

Prescription writes are restricted to the doctor currently assigned to the finalized encounter. `ADMIN` and the assigned doctor may read prescriptions and history. Patients may read only finalized prescriptions for their own encounter. Receptionists and lab technicians cannot create or modify prescriptions. Finalized prescriptions are immutable.

- `POST /api/v1/encounters/{encounterId}/prescriptions` creates a draft prescription; returns `201 Created`.
- `GET /api/v1/encounters/{encounterId}/prescriptions` lists authorized prescriptions; returns `200 OK`.
- `GET /api/v1/prescriptions/{prescriptionId}` retrieves a prescription; returns `200 OK`.
- `PUT /api/v1/prescriptions/{prescriptionId}` replaces draft items and records a full history snapshot; returns `200 OK`.
- `POST /api/v1/prescriptions/{prescriptionId}/finalize` finalizes a draft and records the final snapshot; returns `200 OK`.
- `GET /api/v1/prescriptions/{prescriptionId}/history` returns history to admins and clinicians; returns `200 OK`.

Prescription requests contain a non-empty `items` array. Every item requires `medicineName`, `dosage`, `route`, `frequency`, `duration`, and positive `quantity`; `instructions` is optional. The backend does not generate medical instructions.

# Laboratory

Orders may be placed by the assigned doctor. Lab technicians use the worklist and enter result versions; only the assigned doctor may verify the latest version. Receptionists cannot change laboratory data. Patients see only the latest verified result for their own encounter. Superseded versions remain available to authorized clinicians and technicians as history.

- `POST /api/v1/encounters/{encounterId}/lab-orders` creates an order; doctor only; returns `201 Created`.
- `GET /api/v1/lab-orders/worklist?page=0&size=20&status=ORDERED` returns paginated `ORDERED`/`RESULTED` work; lab technicians only; returns `200 OK`.
- `GET /api/v1/encounters/{encounterId}/lab-orders` lists encounter orders for authorized readers; returns `200 OK`.
- `POST /api/v1/lab-orders/{orderId}/results` enters a new result version; lab technician only; returns `201 Created`.
- `GET /api/v1/lab-orders/{orderId}/results` returns authorized result history; returns `200 OK`.
- `POST /api/v1/lab-orders/{orderId}/results/{resultId}/verify` verifies the latest result; assigned doctor only; returns `200 OK`.
- `GET /api/v1/encounters/{encounterId}/verified-lab-results` returns current verified results for authorized clinicians, admins, and the patient owner; returns `200 OK`.

Lab result `resultValue` is required and provided by the technician. `units`, `referenceRange`, and `notes` are optional and are never fabricated. Verification records `verifiedByUserId` and `verifiedAt`. A correction appends a new version linked to the prior result; it does not overwrite historical values.

V5 adds prescription/current-item and prescription-history snapshot tables. V6 adds laboratory orders and append-only result versions with verification metadata. Both migrations use foreign keys and status constraints and preserve existing data.

# Prescriptions

Base paths: `/api/v1/encounters/{encounterId}/prescriptions` and `/api/v1/prescriptions`

Only the doctor assigned to a finalized encounter may create, update, or finalize a prescription. `ADMIN` and the assigned doctor may read prescription history. The patient may read finalized prescriptions for their own encounter, but not draft prescriptions or history. Receptionists and lab technicians cannot create or modify prescriptions.

- `POST /api/v1/encounters/{encounterId}/prescriptions` creates a draft; returns `201 Created`.
- `GET /api/v1/encounters/{encounterId}/prescriptions` lists authorized prescriptions; returns `200 OK`.
- `GET /api/v1/prescriptions/{prescriptionId}` retrieves one authorized prescription; returns `200 OK`.
- `PUT /api/v1/prescriptions/{prescriptionId}` replaces draft items and appends an immutable history snapshot; returns `200 OK`.
- `POST /api/v1/prescriptions/{prescriptionId}/finalize` finalizes a non-empty draft and records a history snapshot; returns `200 OK`.
- `GET /api/v1/prescriptions/{prescriptionId}/history` returns clinician-visible event and item snapshots; returns `200 OK`.

Each item requires `medicineName`, `dosage`, `route`, `frequency`, `duration`, and positive `quantity`. `instructions` is optional. Finalized prescriptions cannot be modified; invalid edits return `409 Conflict`.

# Laboratory

- `POST /api/v1/encounters/{encounterId}/lab-orders` creates an order; assigned doctor only; returns `201 Created`.
- `GET /api/v1/lab-orders/worklist?page=0&size=20&status=ORDERED` provides the paginated `ORDERED`/`RESULTED` worklist; lab technicians only; returns `200 OK`.
- `GET /api/v1/encounters/{encounterId}/lab-orders` lists authorized encounter orders; returns `200 OK`.
- `POST /api/v1/lab-orders/{orderId}/results` enters a new immutable result version; lab technicians only; returns `201 Created`.
- `GET /api/v1/lab-orders/{orderId}/results` returns result history to authorized roles; patients see only the latest verified version; returns `200 OK`.
- `POST /api/v1/lab-orders/{orderId}/results/{resultId}/verify` verifies the latest result; currently assigned doctor only; returns `200 OK`.
- `GET /api/v1/encounters/{encounterId}/verified-lab-results` returns the latest verified result per order for authorized clinicians, admins, or the patient; returns `200 OK`.

Result values are supplied only by a lab technician. Units and reference ranges are optional and are never generated by the service. Corrections append a new version linked to the prior version. Verification records the verifying user and timestamp; only the latest version can be verified. Receptionists cannot enter or verify results.

V5 creates prescription/current-item and immutable history-snapshot tables. V6 creates laboratory order and append-only result-version tables. Both migrations preserve prior records and constrain their statuses and relationships.

# AI-Assisted Medical Reports

The external AI provider setting is disabled by default (`app.ai.external-provider.enabled=false`). The configured `ReportGenerationService` uses a deterministic mock implementation. It creates a draft from the finalized encounter's recorded diagnosis/notes and the latest verified lab result per order only. It does not infer missing clinical facts, add treatment recommendations, or finalize a report.

Only the currently assigned doctor may generate, edit, submit, approve, or reject reports for an encounter. Patients can read only approved reports belonging to their own record. Admins may read reports and history; they cannot approve reports. Receptionists and lab technicians cannot access reports. Generation always returns `DRAFT`; clinician review is a separate explicit action.

- `POST /api/v1/encounters/{encounterId}/reports/generate` generates a mock draft; returns `201 Created`.
- `GET /api/v1/encounters/{encounterId}/reports` lists reports visible to the authenticated user; returns `200 OK`.
- `GET /api/v1/reports/{reportId}` retrieves an authorized report; returns `200 OK`.
- `GET /api/v1/reports/{reportId}/history` returns immutable content/status snapshots to clinicians and admins; returns `200 OK`.
- `PUT /api/v1/reports/{reportId}/draft` edits an author's draft or rejected report; body `{ "content": "..." }`; returns `200 OK`.
- `POST /api/v1/reports/{reportId}/submit` submits a draft for review; returns `200 OK` with status `IN_REVIEW`.
- `POST /api/v1/reports/{reportId}/approve` approves an in-review report; optional body `{ "notes": "..." }`; returns `200 OK` with status `APPROVED`.
- `POST /api/v1/reports/{reportId}/reject` rejects an in-review report; reviewer notes are required; returns `200 OK` with status `REJECTED`.

V7 adds `medical_reports` and append-only `medical_report_history` content snapshots, including author, reviewer, status, and lifecycle timestamps. Existing migrations V1–V6 are unchanged.