---
Epics & Tickets — Loan Origination Platform
---

EPIC 1 — Project Scaffolding

▎ Goal: Get the monorepo skeleton in place with all three services wired into a root Maven build and local infrastructure ready to run.

- EP1-T1 — Root Maven project with three modules
  - What to build: Root pom.xml declaring loan-service, notification-service, api-gateway-service as Maven modules. Set shared Java version (17), Spring Boot BOM, and common dependency versions.
  - Acceptance criteria: mvn clean install from root compiles all three modules with no errors.
  - How to verify:
    ```
    cd loan_origination
    mvn clean install -DskipTests
    # Expect: BUILD SUCCESS with all three modules listed
    ```
- EP1-T2 — Scaffold loan-service
  - What to build: loan-service/pom.xml (Spring Boot Web, Security, JPA, Flyway, Kafka, jjwt dependencies), main application class, application.yml (port 8081, PostgreSQL datasource, Flyway enabled, Kafka broker config).
  - Acceptance criteria: Service starts on port 8081 with no wired-up logic yet. PostgreSQL and Flyway config present (connection can fail until Docker is up — that's fine).
- EP1-T3 — Scaffold notification-service
  - What to build: notification-service/pom.xml, main class, application.yml (port 8082, its own PostgreSQL datasource, Flyway, Kafka consumer config for both consumer groups).
  - Acceptance criteria: Service starts on port 8082 independently.
- EP1-T4 — Scaffold api-gateway-service
  - What to build: api-gateway-service/pom.xml (Spring Cloud Gateway dependency), main class, application.yml (port 8080, routes placeholder pointing to loan-service).
  - Acceptance criteria: Service starts on port 8080. No routing logic yet, but app boots cleanly.
- EP1-T5 — docker-compose.yml
  - What to build: Compose file with: PostgreSQL 15 for loan-service (port 5432), PostgreSQL 15 for notification-service (port 5433), Zookeeper, Kafka 3.x (port 9092). No Spring Boot containers yet — developers run the
    services locally via Maven.
  - Acceptance criteria: docker compose up starts all four infrastructure containers cleanly. Both databases are reachable, Kafka broker is reachable.

---

EPIC 2 — loan-service: Domain & Database

▎ Goal: All core entities, Flyway migrations, and JPA repositories in place.

- EP2-T1 — Flyway migration: users table
  - What to build: V1\_\_create_users.sql — columns: id, first_name, last_name, email (unique), password, role (enum), created_at, is_active.
  - Acceptance criteria: Table created on startup. No data seeded yet.
- EP2-T2 — Flyway migration: borrowers table
  - What to build: V2\_\_create_borrowers.sql — columns: id, user_id (FK → users), first_name, last_name, email, phone, credit_score, annual_income.
  - Acceptance criteria: Table created with FK constraint on users.
- EP2-T3 — Flyway migration: loan_applications table
  - What to build: V3\_\_create_loan_applications.sql — columns: id, borrower_id (FK), created_by (FK → users), loan_amount, property_address, status (enum), created_at, updated_at.
  - Acceptance criteria: Table created with both FK constraints.
- EP2-T4 — Flyway migrations: assessment, decision, documents, audit_log tables
  - What to build: Four migration files — V4 (property_assessments), V5 (underwriting_decisions), V6 (loan_documents), V7 (audit_logs) — matching the spec fields exactly.
  - Acceptance criteria: All four tables created with correct FK constraints on startup.
- EP2-T5 — JPA entities
  - What to build: User, Borrower, LoanApplication, PropertyAssessment, UnderwritingDecision, LoanDocument, AuditLog entities with correct @Entity, @Table, @ManyToOne/@OneToMany annotations. LoanStatus and UserRole
    enums stored as strings.
  - Acceptance criteria: Entities map 1:1 to the Flyway-created tables. App starts without Hibernate schema errors.
- EP2-T6 — JPA repositories
  - What to build: Spring Data JPA repository interfaces for all six primary entities. Add findByBorrowerId, findByStatus, findByCreatedBy query methods where the API will need them.
  - Acceptance criteria: Repositories are injectable and compile. No manual queries yet.

---

EPIC 3 — loan-service: Auth & Security

▎ Goal: JWT generation/validation, Spring Security filter chain, and working register/login endpoints.

- EP3-T1 — JwtUtil
  - What to build: JwtUtil class using jjwt 0.12.x. Methods: generateToken(userId, role) → signed JWT with 24h expiry; validateToken(token) → boolean; extractUserId(token) → Long; extractRole(token) → String.
  - Acceptance criteria: Unit-testable in isolation. Token round-trips correctly (generate → extract userId and role).
- EP3-T2 — JwtFilter
  - What to build: OncePerRequestFilter that reads Authorization: Bearer <token>, calls JwtUtil.validateToken, and sets SecurityContextHolder with a UsernamePasswordAuthenticationToken carrying userId and role as
    principals/authorities.
  - Acceptance criteria: Requests with valid token reach the controller. Requests with missing/expired/tampered token get 401 before hitting any controller.
- EP3-T3 — SecurityConfig
  - What to build: Spring Security 6 config. Public routes: POST /api/auth/\*_. All other routes require authentication. Role-based rules per the spec (e.g., only BORROWER can POST /api/loans, only DISBURSEMENT can
    PATCH /api/loans/_/disburse). Stateless session. Register JwtFilter before UsernamePasswordAuthenticationFilter.
  - Acceptance criteria: Hitting a protected endpoint with no token returns 401. Hitting with a valid token but wrong role returns 403.
- EP3-T4 — AuthController + AuthService
  - What to build: POST /api/auth/register — creates User + Borrower profile (if role is BORROWER), hashes password with BCrypt. POST /api/auth/login — validates credentials, returns JWT in response body. AuthService
    encapsulates the logic.
  - Acceptance criteria: Can register a BORROWER and a CREDIT_OFFICER. Can log in with either and receive a valid JWT. Wrong password returns 401.
- EP3-T5 — Auth DTOs + global error structure
  - What to build: RegisterRequest, LoginRequest, LoginResponse, and the standard ErrorResponse DTO (status, error, message, timestamp). Wire GlobalExceptionHandler (@RestControllerAdvice) for 400, 401, 403, 404, and
    unhandled exceptions — all returning ErrorResponse JSON.
  - Acceptance criteria: Every error from the auth flow returns the consistent JSON shape defined in the spec. No stack traces exposed to the client.

---

EPIC 4 — loan-service: Loan REST API

▎ Goal: All loan lifecycle endpoints working with role enforcement and audit logging on every status change.

- EP4-T1 — POST /api/loans (submit application)
  - What to build: BORROWER submits a new loan. Reads X-User-Id header (from gateway) to find the Borrower profile. Validates loanAmount > 0. Creates LoanApplication in APPLIED state. Writes AuditLog entry.
  - Acceptance criteria: BORROWER can create a loan. Non-BORROWER gets 403. loanAmount ≤ 0 returns 400 with spec error format.
- EP4-T2 — GET /api/loans (list loans, role-filtered)
  - What to build: Returns loans visible to the caller's role: BORROWER sees only their own; CREDIT_OFFICER sees APPLIED; APPRAISER sees UNDER_REVIEW; UNDERWRITER sees ASSESSED; LEGAL sees APPROVED; DISBURSEMENT sees
    LEGAL_REVIEW; ADMIN sees all. Returns list of LoanSummaryDTO.
  - Acceptance criteria: Each role gets exactly the loans they should see. Empty list (not 403) if no matching loans exist.
- EP4-T3 — GET /api/loans/{id} (get single loan)
  - What to build: Returns LoanDetailDTO (loan + borrower + assessment + decision + documents). Applies same visibility rules as list — returns 403 if the caller has no permission to see this loan's current state.
  - Acceptance criteria: Correct loan returned for authorized caller. 403 for unauthorized. 404 for nonexistent loan.
- EP4-T4 — PATCH /api/loans/{id}/status (status transition + AuditLog)
  - What to build: Validates the transition is legal per the spec state machine (e.g., APPLIED → UNDER_REVIEW only by CREDIT_OFFICER, ASSESSED → APPROVED/REJECTED only by UNDERWRITER). Writes AuditLog on every
    successful transition. Returns 400 for invalid transitions.
  - Acceptance criteria: Valid transitions update status and write audit entry. Invalid transitions return 400 with clear message. Role mismatches return 403.
- EP4-T5 — POST /api/loans/{id}/assessment (APPRAISER)
  - What to build: APPRAISER submits PropertyAssessment for a loan in UNDER_REVIEW state. Transitions loan to ASSESSED. Writes AuditLog.
  - Acceptance criteria: Assessment created, loan moves to ASSESSED. Returns 400 if loan is not in UNDER_REVIEW. Returns 403 for non-APPRAISER.
- EP4-T6 — POST /api/loans/{id}/decision (UNDERWRITER)
  - What to build: UNDERWRITER submits UnderwritingDecision (APPROVED or REJECTED) for a loan in ASSESSED state. Transitions loan to APPROVED or REJECTED accordingly. Writes AuditLog.
  - Acceptance criteria: Decision persisted, loan status updated. 400 if loan not in ASSESSED. 403 for non-UNDERWRITER.
- EP4-T7 — POST /api/loans/{id}/documents (LEGAL)
  - What to build: LEGAL uploads a document reference (path string, no real file). Loan must be in LEGAL_REVIEW state. Writes LoanDocument record. Does not change loan status.
  - Acceptance criteria: Document record created. 400 if loan not in LEGAL_REVIEW. 403 for non-LEGAL.
- EP4-T8 — PATCH /api/loans/{id}/disburse (DISBURSEMENT)
  - What to build: DISBURSEMENT moves loan from LEGAL_REVIEW to DISBURSED. Writes AuditLog.
  - Acceptance criteria: Loan moves to DISBURSED. 400 if not in LEGAL_REVIEW. 403 for non-DISBURSEMENT.

---

EPIC 5 — loan-service: Kafka Producer

▎ Goal: Every loan status change publishes an event to loan-status-changes.

- EP5-T1 — Kafka producer configuration
  - What to build: KafkaProducerConfig bean — bootstrap servers from application.yml, StringSerializer for key, JsonSerializer for value. Topic name as a constant.
  - Acceptance criteria: Bean wires up cleanly. No errors on startup when Kafka is running.
- EP5-T2 — LoanStatusEvent model + KafkaProducerService
  - What to build: LoanStatusEvent POJO (loanId, fromStatus, toStatus, changedBy, timestamp, notifyRole). KafkaProducerService.publish(LoanStatusEvent) sends to topic loan-status-changes.
  - Acceptance criteria: Publishing a test event sends a message to Kafka (verifiable via Kafka CLI consumer).
- EP5-T3 — Wire producer into all status change flows
  - What to build: Call KafkaProducerService.publish(...) immediately after every successful status transition (EP4-T4 through EP4-T8). The changedBy field is the email pulled from the User entity for the X-User-Id
    header value. The notifyRole field is computed from a toStatus → role mapping in loan-service before publishing (e.g. UNDER_REVIEW → CREDIT_OFFICER, ASSESSED → APPRAISER, etc.).
  - Acceptance criteria: Every status change in the system produces a Kafka event with notifyRole populated. Verified end-to-end: hit API → check Kafka topic for event.

---

EPIC 6 — notification-service: Kafka Consumers

▎ Goal: Two independent consumers process every loan status event — one for reporting, one for notifications.

- EP6-T1 — notification-service database schema (Flyway)
  - What to build: V1**create_loan_status_events.sql (persisted event log: loanId, fromStatus, toStatus, changedBy, receivedAt) and V2**create_notification_log.sql (notificationId, loanId, notifiedRole, message,
    notifiedAt).
  - Acceptance criteria: Both tables created on notification-service startup.
- EP6-T2 — Kafka consumer configuration
  - What to build: Two @Bean consumer factory + listener container factory pairs — one for reporting-group, one for notification-group. Both use earliest auto offset reset. JsonDeserializer for LoanStatusEvent.
  - Acceptance criteria: Both consumer groups connect to Kafka on startup (visible in Kafka consumer group list).
- EP6-T3 — LoanStatusEvent model (notification-service side)
  - What to build: Mirror of the producer's LoanStatusEvent POJO in notification-service, including the notifyRole field. Owned independently — no shared library.
  - Acceptance criteria: Deserializes correctly from the JSON the producer sends, including notifyRole.
- EP6-T4 — ReportingConsumer
  - What to build: Listens on loan-status-changes with reporting-group. Persists each event to loan_status_events table. Idempotent: check if a record for this loanId + toStatus already exists before inserting.
  - Acceptance criteria: Each unique status change is persisted exactly once. Duplicate events do not create duplicate rows.
- EP6-T5 — NotificationConsumer
  - What to build: Listens on loan-status-changes with notification-group. Reads event.getNotifyRole() directly from the event payload — no database query. Logs simulated notification to console and writes a row to
    notification_log. Idempotent: check if a notification for this loanId + notifyRole already exists before inserting.
  - Acceptance criteria: Each status change logs a line like [NOTIFY] LEGAL team notified for loan 42. Notification log row written. Duplicate events do not produce duplicate log entries.

---

EPIC 7 — api-gateway-service: Routing & JWT Validation

▎ Goal: Gateway validates JWT, attaches trusted headers, and routes requests to loan-service.

- EP7-T1 — Route configuration
  - What to build: Spring Cloud Gateway routes in application.yml — all /api/auth/** and /api/loans/** traffic proxied to loan-service at http://localhost:8081. No auth filter on /api/auth/\*\*.
  - Acceptance criteria: POST http://localhost:8080/api/auth/login reaches loan-service and returns a JWT.
- EP7-T2 — JwtUtil in api-gateway-service
  - What to build: Copy of JwtUtil scoped to validation only (no token generation needed here). validateToken, extractUserId, extractRole — same jjwt 0.12.x implementation. Owned independently, no shared library.
  - Acceptance criteria: Correctly validates tokens generated by loan-service (same secret in both application.yml files).
- EP7-T3 — JwtAuthenticationGatewayFilter
  - What to build: A GatewayFilter (or GlobalFilter) that: extracts the Authorization header, validates the token, and mutates the downstream request to add X-User-Id and X-User-Role headers. Missing/invalid token
    returns 401 before forwarding. Applied to all routes except /api/auth/\*\*.
  - Acceptance criteria: Authenticated request reaches loan-service with both headers present. Unauthenticated request is blocked at the gateway with 401 and never reaches loan-service.
- EP7-T4 — End-to-end smoke test via gateway
  - What to build: No code — Postman verification. Walk the full happy path: register → login → submit loan → move to UNDER_REVIEW → all through port 8080.
  - Acceptance criteria: Full lifecycle works end-to-end through the gateway. AuditLog populated. Kafka events visible. Notification-service logs show simulated notifications.

---

EPIC 8 — Infrastructure & CI/CD

▎ Goal: One-command local stack and automated cloud deployment pipeline.

- EP8-T1 — Finalize docker-compose.yml with all services
  - What to build: Add loan-service, notification-service, api-gateway-service as containers to the existing Compose file. Configure environment variables (DB URLs, Kafka broker, JWT secret). Set depends_on so services
    start after their dependencies.
  - Acceptance criteria: docker compose up --build starts all seven containers. All three services are healthy and reachable on their respective ports.
- EP8-T2 — GitHub Actions CI/CD pipeline
  - What to build: .github/workflows/deploy.yml — triggers on push to main. Steps: mvn test (root), mvn package for each service, build Docker image per service, push to ECR, SSH deploy to EC2.
  - Acceptance criteria: Pipeline file is syntactically valid and follows the spec's flow. (Full execution requires AWS credentials — not validated locally.)

---

Total: 8 Epics, 33 Tickets

Dependency order is already respected — each epic builds on the one before it. When you're ready to start, say the word and we'll begin with EP1-T1.
