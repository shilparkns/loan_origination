# Architectural Decisions — Loan Origination Platform

## Overview
This document records key architectural choices and their trade-offs. Use this to understand *why* we built things a certain way.

---

## 1. Event-Driven Architecture with Kafka

**Decision:** Use async event publishing (Kafka) instead of direct service calls for notifications.

**Why:**
- **Decoupling:** notification-service failure doesn't block loan status changes in loan-service
- **Scalability:** Can add multiple consumers (reporting, analytics, notifications) without modifying loan-service
- **Audit Trail:** All state changes persist in Kafka for debugging and replay
- **Fan-out:** One event triggers multiple independent actions (email + report + analytics)

**Trade-off:**
- Added complexity: need Kafka infrastructure
- Eventual consistency: notification may arrive slightly after API returns

**Implementation:**
- EP5-T1 to EP5-T3: Wire up producer in loan-service
- EP6-T1 to EP6-T5: Wire up consumers in notification-service

---

## 2. LoanStatusEvent Design: Compute notifyRole in Producer

**Decision:** loan-service computes which role to notify (e.g., "notify APPRAISER") and includes it in the event. notification-service reads it directly, never queries a DB.

**Why:**
- **Single Source of Truth:** All business logic for "who gets notified after this status change" lives in ONE place (loan-service)
- **No N+1 Queries:** notification-service doesn't need to query a roles table
- **Consistent Notifications:** If we change rules later (e.g., "notify CREDIT_OFFICER after REJECTED"), we only update loan-service

**Trade-off:**
- loan-service must maintain the status → notifyRole mapping
- If mapping gets complex, consider a separate service

**Mapping (from SPEC.md):**
```
APPLIED → UNDER_REVIEW        notify → CREDIT_OFFICER
UNDER_REVIEW → ASSESSED       notify → APPRAISER
ASSESSED → APPROVED/REJECTED  notify → UNDERWRITER
APPROVED → LEGAL_REVIEW       notify → LEGAL
LEGAL_REVIEW → DISBURSED      notify → DISBURSEMENT
```

---

## 3. Role-Based Access Control (RBAC) with JWT

**Decision:** Embed role in JWT token. Enforce role rules in Spring Security before controller.

**Why:**
- **Stateless:** No session lookup needed
- **Fast:** Role check happens once at filter, not in every controller
- **Centralized:** SecurityConfig is the single place to enforce access rules
- **Audit-ready:** JWT contains user identity for AuditLog

**Pattern:**
```
Gateway (JwtAuthenticationGatewayFilter):
  ├─ Validates token
  ├─ Extracts userId, role
  └─ Adds X-User-Id, X-User-Role headers to downstream request

loan-service (Controller):
  ├─ Reads headers (trusts gateway)
  └─ Passes to service for business logic validation
```

**Trade-off:**
- If role changes mid-session, user keeps old role until token expires
- Gateway and loan-service must use same JWT secret

---

## 4. Eager Loading with JOIN FETCH to Avoid N+1

**Decision:** Use JPA `@Query` with `JOIN FETCH` in repository methods to eagerly load relationships.

**Example:**
```java
@Query("SELECT l FROM LoanApplication l JOIN FETCH l.borrower WHERE l.id = :id")
Optional<LoanApplication> findByIdWithBorrower(@Param("id") Long id);
```

**Why:**
- **Performance:** One SQL query instead of N+1 (1 loan query + N borrower queries)
- **Explicit:** Obvious in code what relationships are loaded
- **Lazy Loading Risk:** Default JPA lazy loading causes hidden queries, hard to debug

**When NOT to use:**
- Loading many loans (JOIN FETCH multiplies result set if you're not careful)
- When you don't need the relationship

---

## 5. State Machine Pattern for Loan Status Transitions

**Decision:** Validate all status transitions in `validateTransition()` helper. Only allow legal transitions.

**Why:**
- **Business Rule Enforcement:** Can't jump from APPLIED directly to DISBURSED
- **Role Enforcement:** CREDIT_OFFICER can move APPLIED→UNDER_REVIEW, but only APPRAISER can move UNDER_REVIEW→ASSESSED
- **Single Place:** All transition rules in one method, easy to audit and modify

**The State Machine (from SPEC.md):**
```
APPLIED → UNDER_REVIEW → ASSESSED → APPROVED → REJECTED
                                        ↓
                                   LEGAL_REVIEW
                                        ↓
                                   DISBURSED
```

**Transition Rules Table:**
| From | To | Role | Notes |
|---|---|---|---|
| APPLIED | UNDER_REVIEW | CREDIT_OFFICER | Initial review |
| UNDER_REVIEW | ASSESSED | APPRAISER | Property assessment |
| ASSESSED | APPROVED / REJECTED | UNDERWRITER | Final decision |
| APPROVED | LEGAL_REVIEW | LEGAL | Legal docs review |
| LEGAL_REVIEW | DISBURSED | DISBURSEMENT | Final disbursement |

**Terminal States:** REJECTED, DISBURSED (no further transitions allowed)

---

## 6. AuditLog on Every Status Change

**Decision:** Write AuditLog entry immediately after every status transition.

**Why:**
- **Compliance:** Track who changed what and when
- **Debugging:** Know the full history of a loan
- **User Attribution:** AuditLog.changedBy links to User entity via JWT userId

**What we log:**
- `loanApplication` (which loan)
- `changedBy` (which user, from JWT)
- `fromStatus`, `toStatus` (the transition)
- `notes` (context, e.g., "Underwriting decision: APPROVED")
- `changedAt` (auto-timestamped by @PrePersist)

**Trade-off:**
- Every status change writes an extra DB row
- No way to "undo" changes; only record them

---

## 7. DTO Pattern: Never Expose Entities Directly

**Decision:** All API responses return DTOs, never raw JPA entities.

**Why:**
- **API Contract:** Decouples DB schema from API shape
- **Security:** Don't accidentally expose internal fields (e.g., password hashes)
- **Flexibility:** Can return computed fields (e.g., borrower name concatenation) without DB query
- **Evolution:** Can change DB schema without breaking API

**Example:**
```
Entity (DB): Borrower { firstName, lastName, user (FK), ... }
DTO (API):   LoanSummaryDTO { id, loanAmount, borrowerName (computed), status }
```

---

## 8. Separate Request/Response DTOs

**Decision:** Different DTO for request (`CreateLoanRequest`) vs. response (`LoanApplicationDTO`).

**Why:**
- **Request:** Only fields user provides (e.g., loanAmount, propertyAddress)
- **Response:** Includes computed/server fields (e.g., id, createdAt, status)
- **Validation:** Can add `@Valid` constraints to request DTO only

**Example:**
```
CreateLoanRequest (request):
  - loanAmount
  - propertyAddress

LoanApplicationDTO (response):
  - id
  - loanAmount
  - propertyAddress
  - status (set by server)
  - createdAt (set by server)
```

---

## 9. Error Handling: GlobalExceptionHandler with Consistent JSON

**Decision:** Catch all exceptions in `@RestControllerAdvice`, return consistent ErrorResponse JSON.

**Why:**
- **Consistency:** All errors have same shape `{ status, error, message, timestamp }`
- **No Stack Traces:** Never expose internals to client
- **Logging:** Can log the full exception server-side while returning sanitized JSON

**Response Shape:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Loan amount must be greater than 0",
  "timestamp": "2025-05-24T10:30:00"
}
```

---

## 10. Kafka: String Key, Object Value

**Decision:** Kafka messages have String keys (loan ID) and Object values (JSON event).

**Why:**
- **Partitioning:** All events for same loan go to same partition (order guaranteed per loan)
- **JSON:** Human-readable, language-agnostic, easy to parse in any consumer
- **Tooling:** Can inspect messages with `kafka-console-consumer --from-beginning`

**Trade-off:**
- JSON is larger than binary formats (but we're not at scale where that matters)

---

## 11. Independent Service Ownership: No Shared Libraries

**Decision:** notification-service has its own copy of LoanStatusEvent (not imported from loan-service). Each service owns its data models.

**Why:**
- **Decoupling:** Services don't depend on each other's libraries
- **Independent Deployment:** Can upgrade notification-service without redeploying loan-service
- **Evolution:** Each service can evolve its event format without affecting the other
- **Clear Boundaries:** No hidden dependencies between services

**Trade-off:**
- Code duplication: LoanStatusEvent exists in two places
- Must maintain parity manually (if one changes, update both)

---

## 12. Consumer Groups for Independent Processing

**Decision:** Two consumer groups (`reporting-group`, `notification-group`) both listen to the same `loan-status-changes` topic.

**Why:**
- **Independent Scaling:** ReportingConsumer and NotificationConsumer scale independently
- **Different SLAs:** One can be slower without blocking the other
- **Independent Offsets:** Each group tracks its own consumption position in Kafka
- **Easy to Add New Consumers:** Add a new group (e.g., `analytics-group`) without affecting existing ones

**Pattern:**
```
Topic: loan-status-changes
├─ reporting-group (ReportingConsumer)
│  └─ Persists to loan_status_events table (audit trail)
└─ notification-group (NotificationConsumer)
   └─ Sends notifications (simulated via logging)
```

**Why NOT a single group:**
- If combined, they'd share offset offsets and load-balance messages
- One couldn't retry independently if it failed

---

## 13. Idempotency in Consumers: Deduplication by (Key, Field)

**Decision:** Each consumer checks if the message was already processed before inserting.

**ReportingConsumer:** Dedup by `(loanId, toStatus)`
```java
// Check: does a record with this loanId and toStatus already exist?
repository.findByLoanIdAndToStatus(event.getLoanId(), event.getToStatus())
    .ifPresentOrElse(existing -> {}, () -> insert());
```

**NotificationConsumer:** Dedup by `(loanId, notifyRole)`

**Why:**
- **Kafka Delivery:** Kafka guarantees "at least once" (not exactly once)
- **Duplicate Events:** If broker fails, same message may be redelivered
- **No Duplicate Rows:** Idempotency ensures each unique state change is recorded once

**Trade-off:**
- Extra query per message (small cost for correctness)
- If dedup key is wrong (e.g., using only loanId), duplicate messages silently drop

---

## 14. Kafka Deserialization: Ignore Type Info, Use Default Type

**Decision:** Configure `JsonDeserializer.USE_TYPE_INFO_HEADERS = false` and set `VALUE_DEFAULT_TYPE` to notification-service's LoanStatusEvent.

**Why:**
- **Class Mismatch:** Kafka message contains `com.loanorigination.loanservice.event.LoanStatusEvent` (producer's class)
- **notification-service Expects:** `com.loanorigination.notificationservice.event.LoanStatusEvent` (its own class)
- **Solution:** Tell deserializer "ignore the type in the message, always use my LoanStatusEvent class"

**Without this:**
```
ClassNotFoundException: com.loanorigination.loanservice.event.LoanStatusEvent
```

**Trade-off:**
- Deserializer assumes all messages in the topic are the same type
- Can't handle polymorphic messages (multiple types in one topic)

---

## Future Decisions to Document

- Authentication/Authorization in api-gateway-service
- Kafka consumer error handling and dead-letter queue
- Database migration strategy (Flyway)
- Transaction boundaries (when to use @Transactional)
- Caching strategy (if added)
