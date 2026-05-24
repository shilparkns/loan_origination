package com.loanorigination.loanservice.service;

import com.loanorigination.loanservice.dto.CreateLoanRequest;
import com.loanorigination.loanservice.dto.LoanApplicationDTO;
import com.loanorigination.loanservice.dto.LoanDetailDTO;
import com.loanorigination.loanservice.dto.LoanSummaryDTO;
import com.loanorigination.loanservice.dto.PropertyAssessmentRequest;
import com.loanorigination.loanservice.entity.AuditLog;
import com.loanorigination.loanservice.entity.Borrower;
import com.loanorigination.loanservice.entity.LoanApplication;
import com.loanorigination.loanservice.entity.PropertyAssessment;
import com.loanorigination.loanservice.entity.UnderwritingDecision;
import com.loanorigination.loanservice.entity.User;
import com.loanorigination.loanservice.enums.LoanStatus;
import com.loanorigination.loanservice.repository.AuditLogRepository;
import com.loanorigination.loanservice.repository.BorrowerRepository;
import com.loanorigination.loanservice.repository.LoanApplicationRepository;
import com.loanorigination.loanservice.repository.LoanDocumentRepository;
import com.loanorigination.loanservice.repository.PropertyAssessmentRepository;
import com.loanorigination.loanservice.repository.UnderwritingDecisionRepository;
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
    private final PropertyAssessmentRepository propertyAssessmentRepository;
    private final UnderwritingDecisionRepository underwritingDecisionRepository;
    private final LoanDocumentRepository loanDocumentRepository;

    @Autowired
    public LoanService(LoanApplicationRepository loanApplicationRepository,
                       BorrowerRepository borrowerRepository,
                       AuditLogRepository auditLogRepository,
                       UserRepository userRepository,
                       PropertyAssessmentRepository propertyAssessmentRepository,
                       UnderwritingDecisionRepository underwritingDecisionRepository,
                       LoanDocumentRepository loanDocumentRepository) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.borrowerRepository = borrowerRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.propertyAssessmentRepository = propertyAssessmentRepository;
        this.underwritingDecisionRepository = underwritingDecisionRepository;
        this.loanDocumentRepository = loanDocumentRepository;
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

    // Fetches a single loan with full details (borrower, assessment, decision, documents).
    // Applies same role-based visibility rules as getLoansByRole.
    // Returns null if loan not found or user is not authorized to view it.
    public LoanDetailDTO getLoanDetailById(Long loanId, Long userId, String userRole) {
        LoanApplication loan = loanApplicationRepository.findByIdWithBorrower(loanId).orElse(null);
        if (loan == null) {
            return null;
        }

        // Check authorization: user must be able to see this loan based on role and status.
        boolean authorized = isAuthorizedToViewLoan(userId, userRole, loan);
        if (!authorized) {
            return null;
        }

        // Fetch related data (optional — may not exist yet).
        PropertyAssessment assessment = propertyAssessmentRepository.findByLoanApplicationId(loanId).orElse(null);
        UnderwritingDecision decision = underwritingDecisionRepository.findByLoanApplicationId(loanId).orElse(null);
        var documents = loanDocumentRepository.findByLoanApplicationId(loanId);

        // Build LoanDetailDTO.
        LoanDetailDTO detail = new LoanDetailDTO();
        detail.setId(loan.getId());
        detail.setLoanAmount(loan.getLoanAmount());
        detail.setPropertyAddress(loan.getPropertyAddress());
        detail.setStatus(loan.getStatus());
        detail.setCreatedAt(loan.getCreatedAt());

        // Borrower info.
        Borrower borrower = loan.getBorrower();
        detail.setBorrowerFirstName(borrower.getFirstName());
        detail.setBorrowerLastName(borrower.getLastName());
        detail.setBorrowerEmail(borrower.getEmail());
        detail.setBorrowerPhone(borrower.getPhone());
        detail.setBorrowerCreditScore(borrower.getCreditScore());
        detail.setBorrowerAnnualIncome(borrower.getAnnualIncome());

        // Assessment (if exists).
        if (assessment != null) {
            detail.setAssessedValue(assessment.getAssessedValue());
            detail.setAssessedAt(assessment.getAssessedAt());
        }

        // Decision (if exists).
        if (decision != null) {
            detail.setUnderwritingDecision(decision.getDecision());
            detail.setDecisionNotes(decision.getNotes());
            detail.setDecidedAt(decision.getDecidedAt());
        }

        // Documents.
        if (!documents.isEmpty()) {
            var docSummaries = documents.stream()
                    .map(doc -> new LoanDetailDTO.DocumentSummary(doc.getId(), doc.getFilePath()))
                    .collect(Collectors.toList());
            detail.setDocuments(docSummaries);
        }

        return detail;
    }

    // APPRAISER submits a property assessment for a loan in UNDER_REVIEW state.
    // Creates PropertyAssessment, transitions loan to ASSESSED, and writes AuditLog.
    // Throws IllegalArgumentException if loan not found, not in UNDER_REVIEW, or user is not APPRAISER.
    public LoanApplicationDTO submitAssessment(Long loanId, Long userId, String userRole, PropertyAssessmentRequest request) {
        if (!"APPRAISER".equals(userRole)) {
            throw new IllegalArgumentException("Only APPRAISER can submit assessments");
        }

        LoanApplication loan = loanApplicationRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getStatus() != LoanStatus.UNDER_REVIEW) {
            throw new IllegalArgumentException("Loan must be in UNDER_REVIEW status to submit assessment");
        }

        User appraiser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Create PropertyAssessment.
        PropertyAssessment assessment = PropertyAssessment.builder()
                .loanApplication(loan)
                .appraiser(appraiser)
                .assessedValue(request.getAssessedValue())
                .build();

        propertyAssessmentRepository.save(assessment);

        // Transition loan to ASSESSED.
        loan.setStatus(LoanStatus.ASSESSED);
        LoanApplication updatedLoan = loanApplicationRepository.save(loan);

        // Write AuditLog.
        AuditLog auditLog = AuditLog.builder()
                .loanApplication(updatedLoan)
                .changedBy(appraiser)
                .fromStatus(LoanStatus.UNDER_REVIEW.toString())
                .toStatus(LoanStatus.ASSESSED.toString())
                .notes("Property assessment submitted")
                .build();

        auditLogRepository.save(auditLog);

        return new LoanApplicationDTO(
                updatedLoan.getId(),
                updatedLoan.getLoanAmount(),
                updatedLoan.getPropertyAddress(),
                updatedLoan.getStatus(),
                updatedLoan.getCreatedAt()
        );
    }

    // Transitions a loan to a new status if the transition is valid and user is authorized.
    // Writes an AuditLog entry on success.
    // Returns the updated LoanApplicationDTO, or throws exception on invalid transition/role.
    public LoanApplicationDTO transitionLoanStatus(Long loanId, Long userId, String userRole, String toStatusStr) {
        LoanApplication loan = loanApplicationRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        LoanStatus toStatus = LoanStatus.valueOf(toStatusStr);
        LoanStatus fromStatus = loan.getStatus();

        // Validate the transition and check role authorization.
        validateTransition(fromStatus, toStatus, userRole);

        // Update status and timestamp.
        loan.setStatus(toStatus);
        LoanApplication updatedLoan = loanApplicationRepository.save(loan);

        // Get the User entity for AuditLog.
        User changedBy = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Write AuditLog entry.
        AuditLog auditLog = AuditLog.builder()
                .loanApplication(updatedLoan)
                .changedBy(changedBy)
                .fromStatus(fromStatus.toString())
                .toStatus(toStatus.toString())
                .notes("Status changed from " + fromStatus + " to " + toStatus)
                .build();

        auditLogRepository.save(auditLog);

        return new LoanApplicationDTO(
                updatedLoan.getId(),
                updatedLoan.getLoanAmount(),
                updatedLoan.getPropertyAddress(),
                updatedLoan.getStatus(),
                updatedLoan.getCreatedAt()
        );
    }

    // Validates if a status transition is legal and if the user's role allows it.
    // Throws IllegalArgumentException if invalid.
    private void validateTransition(LoanStatus fromStatus, LoanStatus toStatus, String userRole) {
        switch (fromStatus) {
            case APPLIED:
                if (toStatus != LoanStatus.UNDER_REVIEW) {
                    throw new IllegalArgumentException("From APPLIED, can only transition to UNDER_REVIEW");
                }
                if (!"CREDIT_OFFICER".equals(userRole)) {
                    throw new IllegalArgumentException("Only CREDIT_OFFICER can move loan from APPLIED to UNDER_REVIEW");
                }
                break;

            case UNDER_REVIEW:
                if (toStatus != LoanStatus.ASSESSED) {
                    throw new IllegalArgumentException("From UNDER_REVIEW, can only transition to ASSESSED");
                }
                if (!"APPRAISER".equals(userRole)) {
                    throw new IllegalArgumentException("Only APPRAISER can move loan from UNDER_REVIEW to ASSESSED");
                }
                break;

            case ASSESSED:
                if (toStatus != LoanStatus.APPROVED && toStatus != LoanStatus.REJECTED) {
                    throw new IllegalArgumentException("From ASSESSED, can only transition to APPROVED or REJECTED");
                }
                if (!"UNDERWRITER".equals(userRole)) {
                    throw new IllegalArgumentException("Only UNDERWRITER can move loan from ASSESSED to " + toStatus);
                }
                break;

            case APPROVED:
                if (toStatus != LoanStatus.LEGAL_REVIEW) {
                    throw new IllegalArgumentException("From APPROVED, can only transition to LEGAL_REVIEW");
                }
                if (!"LEGAL".equals(userRole)) {
                    throw new IllegalArgumentException("Only LEGAL can move loan from APPROVED to LEGAL_REVIEW");
                }
                break;

            case LEGAL_REVIEW:
                if (toStatus != LoanStatus.DISBURSED) {
                    throw new IllegalArgumentException("From LEGAL_REVIEW, can only transition to DISBURSED");
                }
                if (!"DISBURSEMENT".equals(userRole)) {
                    throw new IllegalArgumentException("Only DISBURSEMENT can move loan from LEGAL_REVIEW to DISBURSED");
                }
                break;

            case REJECTED:
            case DISBURSED:
                throw new IllegalArgumentException("Loan in " + fromStatus + " status cannot be transitioned");

            default:
                throw new IllegalArgumentException("Unknown status: " + fromStatus);
        }
    }

    // Determines if a user can view a loan based on their role and the loan's status.
    private boolean isAuthorizedToViewLoan(Long userId, String userRole, LoanApplication loan) {
        switch (userRole) {
            case "BORROWER":
                return loan.getBorrower().getUser().getId().equals(userId);
            case "CREDIT_OFFICER":
                return loan.getStatus() == LoanStatus.APPLIED;
            case "APPRAISER":
                return loan.getStatus() == LoanStatus.UNDER_REVIEW;
            case "UNDERWRITER":
                return loan.getStatus() == LoanStatus.ASSESSED;
            case "LEGAL":
                return loan.getStatus() == LoanStatus.APPROVED;
            case "DISBURSEMENT":
                return loan.getStatus() == LoanStatus.LEGAL_REVIEW;
            case "ADMIN":
                return true;
            default:
                return false;
        }
    }
}

