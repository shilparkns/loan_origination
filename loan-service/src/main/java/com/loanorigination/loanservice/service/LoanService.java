package com.loanorigination.loanservice.service;

import com.loanorigination.loanservice.dto.CreateLoanRequest;
import com.loanorigination.loanservice.dto.LoanApplicationDTO;
import com.loanorigination.loanservice.dto.LoanSummaryDTO;
import com.loanorigination.loanservice.entity.AuditLog;
import com.loanorigination.loanservice.entity.Borrower;
import com.loanorigination.loanservice.entity.LoanApplication;
import com.loanorigination.loanservice.entity.User;
import com.loanorigination.loanservice.enums.LoanStatus;
import com.loanorigination.loanservice.repository.AuditLogRepository;
import com.loanorigination.loanservice.repository.BorrowerRepository;
import com.loanorigination.loanservice.repository.LoanApplicationRepository;
import com.loanorigination.loanservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

// Business logic for loan applications.
// Handles creating loans, transitioning statuses, and audit logging.
@Service
public class LoanService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final BorrowerRepository borrowerRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Autowired
    public LoanService(LoanApplicationRepository loanApplicationRepository,
                       BorrowerRepository borrowerRepository,
                       AuditLogRepository auditLogRepository,
                       UserRepository userRepository) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.borrowerRepository = borrowerRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    // BORROWER submits a new loan application.
    // Reads the Borrower profile via userId, creates a LoanApplication in APPLIED state,
    // and logs the action to AuditLog.
    public LoanApplicationDTO createLoan(Long userId, CreateLoanRequest request) {
        // Find the Borrower associated with this user.
        // If not found, throw an exception (Borrower must exist — created at registration for BORROWER role).
        Borrower borrower = borrowerRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Borrower profile not found for user"));

        // Get the User entity to record who created this loan (for AuditLog).
        User createdBy = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Create the LoanApplication with APPLIED status.
        LoanApplication loanApplication = LoanApplication.builder()
                .borrower(borrower)
                .createdBy(createdBy)
                .loanAmount(request.getLoanAmount())
                .propertyAddress(request.getPropertyAddress())
                .status(LoanStatus.APPLIED)
                .build();

        // Save to database.
        LoanApplication savedLoan = loanApplicationRepository.save(loanApplication);

        // Log the action: who created this loan, when, and what status it entered.
        AuditLog auditLog = AuditLog.builder()
                .loanApplication(savedLoan)
                .changedBy(createdBy)
                .fromStatus(null)
                .toStatus(LoanStatus.APPLIED.toString())
                .notes("Loan application submitted")
                .build();

        auditLogRepository.save(auditLog);

        // Convert to DTO and return.
        return new LoanApplicationDTO(
                savedLoan.getId(),
                savedLoan.getLoanAmount(),
                savedLoan.getPropertyAddress(),
                savedLoan.getStatus(),
                savedLoan.getCreatedAt()
        );
    }

    // Fetches loans visible to the caller based on their role.
    // Different roles see different loans per the spec state machine:
    //   BORROWER      → only their own loans
    //   CREDIT_OFFICER → APPLIED status
    //   APPRAISER     → UNDER_REVIEW status
    //   UNDERWRITER   → ASSESSED status
    //   LEGAL         → APPROVED status
    //   DISBURSEMENT  → LEGAL_REVIEW status
    //   ADMIN         → all loans
    public List<LoanSummaryDTO> getLoansByRole(Long userId, String userRole) {
        List<LoanApplication> loans;

        switch (userRole) {
            case "BORROWER":
                // BORROWER sees only their own loans.
                loans = loanApplicationRepository.findByBorrowerIdWithBorrower(userId);
                break;
            case "CREDIT_OFFICER":
                // CREDIT_OFFICER sees loans in APPLIED status.
                loans = loanApplicationRepository.findByStatusWithBorrower(LoanStatus.APPLIED);
                break;
            case "APPRAISER":
                // APPRAISER sees loans in UNDER_REVIEW status.
                loans = loanApplicationRepository.findByStatusWithBorrower(LoanStatus.UNDER_REVIEW);
                break;
            case "UNDERWRITER":
                // UNDERWRITER sees loans in ASSESSED status.
                loans = loanApplicationRepository.findByStatusWithBorrower(LoanStatus.ASSESSED);
                break;
            case "LEGAL":
                // LEGAL sees loans in APPROVED status.
                loans = loanApplicationRepository.findByStatusWithBorrower(LoanStatus.APPROVED);
                break;
            case "DISBURSEMENT":
                // DISBURSEMENT sees loans in LEGAL_REVIEW status.
                loans = loanApplicationRepository.findByStatusWithBorrower(LoanStatus.LEGAL_REVIEW);
                break;
            case "ADMIN":
                // ADMIN sees all loans.
                loans = loanApplicationRepository.findAllWithBorrower();
                break;
            default:
                // Unknown role — return empty list.
                return List.of();
        }

        // Convert to LoanSummaryDTO and return.
        return loans.stream()
                .map(loan -> new LoanSummaryDTO(
                        loan.getId(),
                        loan.getLoanAmount(),
                        loan.getBorrower().getFirstName() + " " + loan.getBorrower().getLastName(),
                        loan.getStatus()
                ))
                .collect(Collectors.toList());
    }
}

