# Loan Origination & Due Diligence Platform

## Project Spec v1.0

---

## OVERVIEW

A platform for end-to-end loan origination process. Multiple teams interact with loan applications at different stages. The system enforces stage-based access, tracks every state change, and notifies downstream teams automatically via Kafka events.

---

## USERS & ROLES

| Role           | Permissions                                                   |
| -------------- | ------------------------------------------------------------- |
| BORROWER       | Submit application, view own application status               |
| CREDIT_OFFICER | View applications in APPLIED state, move to UNDER_REVIEW      |
| APPRAISER      | View applications in UNDER_REVIEW, submit property assessment |
| UNDERWRITER    | View assessed applications, approve or reject                 |
| LEGAL          | View approved applications, upload documents                  |
| DISBURSEMENT   | View legal-cleared applications, mark as DISBURSED            |
| ADMIN          | Full access, manage users                                     |

---

## LOAN LIFECYCLE STATES

```
APPLIED → UNDER_REVIEW → ASSESSED → APPROVED → LEGAL_REVIEW → DISBURSED
                              └──────────────→ REJECTED (terminal)
```

---

## MODULES

---

### MODULE 1 — Loan Domain & Database

**What it owns:** All core entities and database schema

#### Entities:

**User (owned by auth-service)**

```
- id
- firstName, lastName
- email (unique, used for login)
- password (hashed, never stored plain)
- role (BORROWER, CREDIT_OFFICER, APPRAISER, UNDERWRITER, LEGAL, DISBURSEMENT, ADMIN)
- createdAt
- isActive (admin can deactivate users)
```

**LoanApplication**

```
- id
- borrowerId (Long, points to BORROWER user in auth-service)
- createdById (Long, creator userId from auth-service)
- loanAmount (must be > 0)
- propertyAddress
- status (APPLIED, UNDER_REVIEW, ASSESSED, APPROVED, REJECTED, LEGAL_REVIEW, DISBURSED)
- createdAt, updatedAt
```

**PropertyAssessment**

```
- id
- loanApplication (FK → LoanApplication)
- appraiserId (Long, APPRAISER userId from auth-service)
- assessedValue
- assessedAt
```

**UnderwritingDecision**

```
- id
- loanApplication (FK → LoanApplication)
- underwriterId (Long, UNDERWRITER userId from auth-service)
- decision (APPROVED / REJECTED)
- notes
- decidedAt
```

**LoanDocument**

```
- id
- loanApplication (FK → LoanApplication)
- uploadedById (Long, LEGAL userId from auth-service)
- documentType
- filePath (string, no actual file storage)
- uploadedAt
```

**AuditLog**

```
- id
- loanApplication (FK → LoanApplication)
- changedById (Long, userId from auth-service)
- fromStatus
- toStatus
- notes
- changedAt
```

#### Entity Relationships:

```
LoanApplication.borrowerId → auth-service User (role: BORROWER)
LoanApplication.createdById → auth-service User
PropertyAssessment.appraiserId → auth-service User (role: APPRAISER)
UnderwritingDecision.underwriterId → auth-service User (role: UNDERWRITER)
LoanDocument.uploadedById → auth-service User (role: LEGAL)
AuditLog.changedById → auth-service User (any role)
```

#### Edge Cases:

- loanAmount cannot be negative or zero
- Every status change must write to AuditLog with the userId from the JWT token
- password must be stored hashed (BCrypt), never plain text

---

### MODULE 2 — REST API Layer

**What it owns:** All HTTP endpoints, request/response DTOs

#### Endpoints:

```
# Auth (infrastructure, top-level routing)
POST   /auth/register               → Register new user
POST   /auth/login                  → Login, returns JWT
GET    /auth/users/{userId}         → Fetch user by ID (service-to-service)

# Loans (domain resources)
POST   /api/loans                   → Submit new application (BORROWER)
GET    /api/loans                   → List loans (filtered by role + status)
GET    /api/loans/{id}              → Get loan details (role-filtered)
PATCH  /api/loans/{id}/status       → Update loan status (role-dependent)

# Assessments
POST   /api/loans/{id}/assessment   → Submit property assessment (APPRAISER)

# Underwriting
POST   /api/loans/{id}/decision     → Submit underwriting decision (UNDERWRITER)

# Documents
POST   /api/loans/{id}/documents    → Upload document reference (LEGAL)

# Disbursement
PATCH  /api/loans/{id}/disburse     → Mark as disbursed (DISBURSEMENT)
```

#### Rules:

- DTOs for all API responses — never expose entities directly
- Global exception handler — all errors return consistent JSON:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Loan amount must be greater than zero",
  "timestamp": "2025-01-15T10:30:00"
}
```

#### Edge Cases:

- A BORROWER can only see their own loans
- A CREDIT_OFFICER cannot approve or reject a loan
- Invalid status transitions must return 400 with a clear error message
- Accessing a loan you don't have permission to view returns 403

---

### MODULE 3 — Security (Spring Security + JWT)

**What it owns:** Authentication, authorization, token management

#### Flow:

```
User hits POST /auth/login with email + password
        ↓
System validates credentials, generates JWT with role embedded
        ↓
User sends JWT in every subsequent request header:
Authorization: Bearer <token>
        ↓
Gateway filter validates token, extracts userId + role, and forwards trusted headers
        ↓
loan-service applies role + state business rules
```

#### Key Classes:

- `JwtUtil` — generates and validates tokens, extracts role/userId
- `JwtAuthenticationGatewayFilter` — validates JWT at gateway and injects `X-User-Id`/`X-User-Role`
- `SecurityConfig` — defines protected/public routes and role constraints

#### Edge Cases:

- Expired token → 401 Unauthorized
- Valid token but wrong role → 403 Forbidden
- Missing token → 401 Unauthorized
- Tampered token → 401 Unauthorized

---

### MODULE 4 — Kafka Event Pipeline

**What it owns:** Publishing and consuming loan state change events

#### Event Trigger:

Every time a loan status changes, publish an event to Kafka:

```
Topic: loan-status-changes

Event Payload:
{
  "loanId": 123,
  "fromStatus": "APPLIED",
  "toStatus": "UNDER_REVIEW",
  "changedBy": "john@bank.com",
  "timestamp": "2025-01-15T10:30:00"
}
```

#### Status → Role Notification Mapping:

```
APPLIED → UNDER_REVIEW        notify → CREDIT_OFFICER
UNDER_REVIEW → ASSESSED       notify → APPRAISER
ASSESSED → APPROVED/REJECTED  notify → UNDERWRITER
APPROVED → LEGAL_REVIEW       notify → LEGAL
LEGAL_REVIEW → DISBURSED      notify → DISBURSEMENT
```

#### Consumers:

| Consumer             | Listens To          | Action                                                               |
| -------------------- | ------------------- | -------------------------------------------------------------------- |
| ReportingConsumer    | loan-status-changes | Updates reporting/analytics table                                    |
| NotificationConsumer | loan-status-changes | Looks up users with the next-stage role, logs simulated notification |

#### Edge Cases:

- If consumer fails, event must not be lost (use earliest offset)
- Duplicate events must be handled idempotently (check if status already updated before processing)

---

### MODULE 5 — Infrastructure

**What it owns:** Local setup and CI/CD

#### docker-compose.yml spins up:

- PostgreSQL (separate DB per service)
- Kafka 3.x + Zookeeper
- Spring Boot services run locally via Maven

#### GitHub Actions Pipeline:

```
Push to main
      ↓
Run tests
      ↓
Build Docker image
      ↓
Push to ECR
      ↓
Deploy to EC2
```

#### Database Migrations:

- Use Flyway for all schema changes
- Migration files versioned: V1**create_users.sql, V2**create_loans.sql etc.

---

## TECH STACK

| Layer            | Technology                               |
| ---------------- | ---------------------------------------- |
| Language         | Java 17                                  |
| Framework        | Spring Boot 3.x                          |
| Security         | Spring Security 6.x + JWT (jjwt library) |
| Database         | PostgreSQL 15                            |
| ORM              | Spring Data JPA + Hibernate              |
| Migrations       | Flyway                                   |
| Messaging        | Kafka 3.x                                |
| Containerization | Docker + Docker Compose                  |
| Cloud            | AWS (EC2, RDS)                           |
| CI/CD            | GitHub Actions                           |

---

## WHAT IS DELIBERATELY KEPT SIMPLE

- No actual file storage — document paths are just strings in the database
- No real email sending — NotificationConsumer logs to console
- No payment gateway — disbursement is just a status change
- No frontend — Postman is sufficient to demo all flows (we'll add it later)

---

## KEY INTERVIEW TALKING POINTS

- **Audit trail:** Every status change writes to AuditLog with the userId pulled from the JWT token — connects JWT, AuditLog, and User entity in one flow
- **Microservice decoupling:** loan-service stores user IDs and fetches user data from auth-service (no cross-service FK constraints)
- **Kafka design:** Chose event-driven over direct service calls so downstream teams are fully decoupled — a consumer failure doesn't block the origination flow
- **Role-based access:** Roles embedded in JWT, enforced at the Spring Security layer before requests hit controllers
