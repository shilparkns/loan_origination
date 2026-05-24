package com.loanorigination.loanservice.controller;

import com.loanorigination.loanservice.dto.CreateLoanRequest;
import com.loanorigination.loanservice.dto.LoanApplicationDTO;
import com.loanorigination.loanservice.dto.LoanDetailDTO;
import com.loanorigination.loanservice.dto.LoanDocumentRequest;
import com.loanorigination.loanservice.dto.LoanSummaryDTO;
import com.loanorigination.loanservice.dto.PropertyAssessmentRequest;
import com.loanorigination.loanservice.dto.UnderwritingDecisionRequest;
import com.loanorigination.loanservice.dto.UpdateLoanStatusRequest;
import com.loanorigination.loanservice.service.LoanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

// HTTP endpoints for loan operations.
// All endpoints require authentication (except /auth/\* which is in AuthController).
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    @Autowired
    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    // POST /api/loans — BORROWER submits a new loan application.
    // Reads X-User-Id and X-User-Role headers (set by api-gateway).
    // @Valid ensures CreateLoanRequest passes validation (loanAmount > 0, propertyAddress not blank).
    // Returns 201 Created with the created loan details.
    @PostMapping
    public ResponseEntity<LoanApplicationDTO> createLoan(
            @Valid @RequestBody CreateLoanRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole) {

        // Only BORROWER can submit a loan.
        if (!"BORROWER".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Create the loan via LoanService.
        // LoanService handles: looking up Borrower, creating LoanApplication, writing AuditLog.
        LoanApplicationDTO createdLoan = loanService.createLoan(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdLoan);
    }

    // GET /api/loans — List loans visible to the caller's role.
    // Different roles see different loans per the spec:
    //   BORROWER      → only their own
    //   CREDIT_OFFICER → APPLIED status
    //   APPRAISER     → UNDER_REVIEW status
    //   UNDERWRITER   → ASSESSED status
    //   LEGAL         → APPROVED status
    //   DISBURSEMENT  → LEGAL_REVIEW status
    //   ADMIN         → all loans
    // Returns 200 OK with a list of LoanSummaryDTO (empty list if no matching loans).
    @GetMapping
    public ResponseEntity<List<LoanSummaryDTO>> listLoans(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole) {

        List<LoanSummaryDTO> loans = loanService.getLoansByRole(userId, userRole);
        return ResponseEntity.ok(loans);
    }

    // GET /api/loans/{id} — Get single loan with full details.
    // Returns LoanDetailDTO (loan + borrower + assessment + decision + documents).
    // Applies same role-based visibility rules as list endpoint.
    // Returns 404 if loan not found, 403 if unauthorized, 200 if authorized.
    @GetMapping("/{id}")
    public ResponseEntity<LoanDetailDTO> getLoanDetail(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole) {

        LoanDetailDTO detail = loanService.getLoanDetailById(id, userId, userRole);

        if (detail == null) {
            // Could be not found or unauthorized — return 404.
            // Per spec, 403 only for known loans the user can't see; 404 is safer for unknown loans.
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(detail);
    }

    // PATCH /api/loans/{id}/status — Update loan status with validation and audit logging.
    // Validates the transition is legal per the state machine and user has the right role.
    // Returns 200 with updated loan, 400 for invalid transition, 404 for not found.
    @PatchMapping("/{id}/status")
    public ResponseEntity<LoanApplicationDTO> updateLoanStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLoanStatusRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole) {

        try {
            LoanApplicationDTO updatedLoan = loanService.transitionLoanStatus(id, userId, userRole, request.getToStatus());
            return ResponseEntity.ok(updatedLoan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // POST /api/loans/{id}/assessment — APPRAISER submits property assessment.
    // Assessment must have assessed value. Loan must be in UNDER_REVIEW status.
    // Transitions loan to ASSESSED and writes AuditLog.
    // Returns 201 with updated loan, 400 if invalid state/role, 404 if not found.
    @PostMapping("/{id}/assessment")
    public ResponseEntity<LoanApplicationDTO> submitAssessment(
            @PathVariable Long id,
            @Valid @RequestBody PropertyAssessmentRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole) {

        try {
            LoanApplicationDTO updatedLoan = loanService.submitAssessment(id, userId, userRole, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(updatedLoan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // POST /api/loans/{id}/decision — UNDERWRITER submits underwriting decision.
    // Decision must be APPROVED or REJECTED. Loan must be in ASSESSED status.
    // Transitions loan to APPROVED or REJECTED accordingly and writes AuditLog.
    // Returns 201 with updated loan, 400 if invalid state/role, 404 if not found.
    @PostMapping("/{id}/decision")
    public ResponseEntity<LoanApplicationDTO> submitDecision(
            @PathVariable Long id,
            @Valid @RequestBody UnderwritingDecisionRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole) {

        try {
            LoanApplicationDTO updatedLoan = loanService.submitDecision(id, userId, userRole, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(updatedLoan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // POST /api/loans/{id}/documents — LEGAL uploads a document reference.
    // Document must have type and file path. Loan must be in LEGAL_REVIEW status.
    // Does not change loan status. Returns 201 with loan (unchanged).
    // Returns 400 if invalid state/role, 404 if not found.
    @PostMapping("/{id}/documents")
    public ResponseEntity<LoanApplicationDTO> uploadDocument(
            @PathVariable Long id,
            @Valid @RequestBody LoanDocumentRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole) {

        try {
            LoanApplicationDTO loan = loanService.uploadDocument(id, userId, userRole, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(loan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // PATCH /api/loans/{id}/disburse — DISBURSEMENT moves loan to DISBURSED.
    // Loan must be in LEGAL_REVIEW status. Transitions to DISBURSED and writes AuditLog.
    // Returns 200 with updated loan, 400 if invalid state/role, 404 if not found.
    @PatchMapping("/{id}/disburse")
    public ResponseEntity<LoanApplicationDTO> disburseLoan(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole) {

        try {
            LoanApplicationDTO updatedLoan = loanService.disburseLoan(id, userId, userRole);
            return ResponseEntity.ok(updatedLoan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}

