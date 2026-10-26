
# Smart Hospital Management & AI Report System

## Technology restrictions
- Frontend: React with Vite and plain CSS.
- Backend: Java and Spring Boot REST APIs.
- Database: PostgreSQL.
- Do not create a Node.js, Express, Python, or other backend.
- Do not use MongoDB or Firebase.

## Backend rules
- Use controller, service, repository, entity, and DTO layers.
- Use Spring Data JPA and PostgreSQL.
- Validate every incoming request.
- Never expose JPA entities directly as API responses.
- Use Spring Security for authentication and role authorization.
- Hash passwords securely. Never store plaintext passwords.
- Validate appointment conflicts on the server.
- Enforce patient ownership and doctor assignment permissions.
- Use environment variables for credentials and AI API keys.
- Add appropriate error handling and automated tests.

## AI report rules
- Keep AI generation inside the Spring Boot backend.
- Generate drafts using recorded clinical notes and verified laboratory results.
- Do not invent missing measurements or facts.
- Never automatically prescribe medication or finalize a report.
- Require authorized clinician review and approval.
- Record draft creation and approval in audit history.
- Start with a mock report-generation service if no AI provider is configured.

## Frontend rules
- Keep API calls in src/api.
- Keep the API base URL configurable.
- Display loading, success, validation, and error states.
- Do not rely on frontend role checks as the security boundary.
- Make all pages responsive and accessible.

## Development workflow
- Work in small, independently testable phases.
- Before editing, inspect relevant files and propose a plan.
- Run available tests and build commands after changes.
- Report files changed, commands run, and remaining failures.
- Never claim tests passed unless they were actually run.
