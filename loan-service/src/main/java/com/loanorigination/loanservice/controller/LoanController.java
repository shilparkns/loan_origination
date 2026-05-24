package com.loanorigination.loanservice.controller;

import com.loanorigination.loanservice.dto.CreateLoanRequest;
import com.loanorigination.loanservice.dto.LoanApplicationDTO;
import com.loanorigination.loanservice.service.LoanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

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
    // Reads X-User-Id and X-User-Role headers (set by JwtFilter).
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
}
