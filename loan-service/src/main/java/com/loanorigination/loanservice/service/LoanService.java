package com.loanorigination.loanservice.service;

import com.loanorigination.loanservice.client.UserClient;
import com.loanorigination.loanservice.dto.CreateLoanRequest;
import com.loanorigination.loanservice.dto.LoanApplicationDTO;
import com.loanorigination.loanservice.dto.LoanDetailDTO;
import com.loanorigination.loanservice.dto.LoanDocumentRequest;
import com.loanorigination.loanservice.dto.LoanSummaryDTO;
import com.loanorigination.loanservice.dto.PropertyAssessmentRequest;
import com.loanorigination.loanservice.dto.UnderwritingDecisionRequest;
import com.loanorigination.loanservice.dto.UserDto;
import com.loanorigination.loanservice.entity.AuditLog;
import com.loanorigination.loanservice.entity.LoanApplication;
import com.loanorigination.loanservice.entity.LoanDocument;
import com.loanorigination.loanservice.entity.PropertyAssessment;
import com.loanorigination.loanservice.entity.UnderwritingDecision;
import com.loanorigination.loanservice.enums.LoanStatus;
import com.loanorigination.loanservice.event.LoanStatusEvent;
import com.loanorigination.loanservice.repository.AuditLogRepository;
import com.loanorigination.loanservice.repository.LoanApplicationRepository;
import com.loanorigination.loanservice.repository.LoanDocumentRepository;
import com.loanorigination.loanservice.repository.PropertyAssessmentRepository;
import com.loanorigination.loanservice.repository.UnderwritingDecisionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

// Business logic for loan applications.
// Handles creating loans, transitioning statuses, and audit logging.
@Service
public class LoanService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final AuditLogRepository auditLogRepository;
    private final PropertyAssessmentRepository propertyAssessmentRepository;
    private final UnderwritingDecisionRepository underwritingDecisionRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final KafkaProducerService kafkaProducerService;
    private final UserClient userClient;

    @Autowired
    public LoanService(LoanApplicationRepository loanApplicationRepository,
                       AuditLogRepository auditLogRepository,
                       PropertyAssessmentRepository propertyAssessmentRepository,
                       UnderwritingDecisionRepository underwritingDecisionRepository,
                       LoanDocumentRepository loanDocumentRepository,
                       KafkaProducerService kafkaProducerService,
                       UserClient userClient) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.auditLogRepository = auditLogRepository;
        this.propertyAssessmentRepository = propertyAssessmentRepository;
        this.underwritingDecisionRepository = underwritingDecisionRepository;
        this.loanDocumentRepository = loanDocumentRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.userClient = userClient;
    }

    // BORROWER submits a new loan application.
    // Verifies the user is registered in auth-service, then creates a LoanApplication in APPLIED state.
    public LoanApplicationDTO createLoan(Long userId, CreateLoanRequest request) {
        // Verify user exists in auth-service (registered user).
        UserDto userDto = userClient.getUserById(userId);

        // Create the LoanApplication with APPLIED status.
        LoanApplication loanApplication = LoanApplication.builder()
                .borrowerId(userId)
                .createdById(userId)
                .loanAmount(request.getLoanAmount())
                .propertyAddress(request.getPropertyAddress())
                .status(LoanStatus.APPLIED)
                .build();

        // Save to database.
        LoanApplication savedLoan = loanApplicationRepository.save(loanApplication);

        // Log the action: who created this loan, when, and what status it entered.
        AuditLog auditLog = AuditLog.builder()
                .loanApplication(savedLoan)
                .changedById(userId)
                .fromStatus(null)
                .toStatus(LoanStatus.APPLIED.toString())
                .notes("Loan application submitted by " + userDto.getEmail())
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
    // Different roles see different loans per the spec state machine.
    // Fetches borrower names from auth-service.
    public List<LoanSummaryDTO> getLoansByRole(Long userId, String userRole) {
        List<LoanApplication> loans;

        switch (userRole) {
            case "BORROWER":
                loans = loanApplicationRepository.findByBorrowerId(userId);
                break;
            case "CREDIT_OFFICER":
                loans = loanApplicationRepository.findByStatus(LoanStatus.APPLIED);
                break;
            case "APPRAISER":
                loans = loanApplicationRepository.findByStatus(LoanStatus.UNDER_REVIEW);
                break;
            case "UNDERWRITER":
                loans = loanApplicationRepository.findByStatus(LoanStatus.ASSESSED);
                break;
            case "LEGAL":
                loans = loanApplicationRepository.findByStatus(LoanStatus.APPROVED);
                break;
            case "DISBURSEMENT":
                loans = loanApplicationRepository.findByStatus(LoanStatus.LEGAL_REVIEW);
                break;
            case "ADMIN":
                loans = loanApplicationRepository.findAll();
                break;
            default:
                return List.of();
        }

        // Convert to DTOs, fetching borrower names from auth-service.
        return loans.stream()
                .map(loan -> {
                    try {
                        UserDto borrower = userClient.getUserById(loan.getBorrowerId());
                        return new LoanSummaryDTO(
                                loan.getId(),
                                loan.getLoanAmount(),
                                borrower.getFirstName() + " " + borrower.getLastName(),
                                loan.getStatus()
                        );
                    } catch (Exception e) {
                        // If user not found, use loan ID as fallback.
                        return new LoanSummaryDTO(
                                loan.getId(),
                                loan.getLoanAmount(),
                                "User " + loan.getBorrowerId(),
                                loan.getStatus()
                        );
                    }
                })
                .collect(Collectors.toList());
    }

    // Fetches a single loan with full details (borrower, assessment, decision, documents).
    // Applies same role-based visibility rules as getLoansByRole.
    // Returns null if loan not found or user is not authorized to view it.
    public LoanDetailDTO getLoanDetailById(Long loanId, Long userId, String userRole) {
        LoanApplication loan = loanApplicationRepository.findById(loanId).orElse(null);
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

        // Borrower info — fetch from auth-service.
        try {
            UserDto borrowerDto = userClient.getUserById(loan.getBorrowerId());
            detail.setBorrowerFirstName(borrowerDto.getFirstName());
            detail.setBorrowerLastName(borrowerDto.getLastName());
            detail.setBorrowerEmail(borrowerDto.getEmail());
            // Note: UserDto doesn't include phone, credit score, annual income — those are borrower-specific.
            // We could add a BorrowerDto that includes these, or just leave them as null/default.
        } catch (Exception e) {
            // If borrower not found in auth-service, still return loan but without borrower details.
        }

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

        // Fetch user from auth-service.
        UserDto userDto = userClient.getUserById(userId);

        // Create PropertyAssessment.
        PropertyAssessment assessment = PropertyAssessment.builder()
                .loanApplication(loan)
                .appraiserId(userId)
                .assessedValue(request.getAssessedValue())
                .build();

        propertyAssessmentRepository.save(assessment);

        // Transition loan to ASSESSED.
        loan.setStatus(LoanStatus.ASSESSED);
        LoanApplication updatedLoan = loanApplicationRepository.save(loan);

        // Write AuditLog.
        AuditLog auditLog = AuditLog.builder()
                .loanApplication(updatedLoan)
                .changedById(userId)
                .fromStatus(LoanStatus.UNDER_REVIEW.toString())
                .toStatus(LoanStatus.ASSESSED.toString())
                .notes("Property assessment submitted")
                .build();

        auditLogRepository.save(auditLog);

        // Publish event to Kafka.
        publishLoanStatusEvent(LoanStatus.UNDER_REVIEW, LoanStatus.ASSESSED, userDto, updatedLoan.getId());

        return new LoanApplicationDTO(
                updatedLoan.getId(),
                updatedLoan.getLoanAmount(),
                updatedLoan.getPropertyAddress(),
                updatedLoan.getStatus(),
                updatedLoan.getCreatedAt()
        );
    }

    // UNDERWRITER submits an underwriting decision (APPROVED or REJECTED) for a loan in ASSESSED state.
    // Creates UnderwritingDecision, transitions loan to APPROVED or REJECTED, and writes AuditLog.
    // Throws IllegalArgumentException if loan not found, not in ASSESSED, or user is not UNDERWRITER.
    public LoanApplicationDTO submitDecision(Long loanId, Long userId, String userRole, UnderwritingDecisionRequest request) {
        if (!"UNDERWRITER".equals(userRole)) {
            throw new IllegalArgumentException("Only UNDERWRITER can submit decisions");
        }

        LoanApplication loan = loanApplicationRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getStatus() != LoanStatus.ASSESSED) {
            throw new IllegalArgumentException("Loan must be in ASSESSED status to submit decision");
        }

        // Fetch user from auth-service.
        UserDto userDto = userClient.getUserById(userId);

        // Create UnderwritingDecision.
        UnderwritingDecision decision = UnderwritingDecision.builder()
                .loanApplication(loan)
                .underwriterId(userId)
                .decision(request.getDecision())
                .notes(request.getNotes())
                .build();

        underwritingDecisionRepository.save(decision);

        // Transition loan to APPROVED or REJECTED based on decision.
        LoanStatus newStatus = "APPROVED".equals(request.getDecision()) ? LoanStatus.APPROVED : LoanStatus.REJECTED;
        loan.setStatus(newStatus);
        LoanApplication updatedLoan = loanApplicationRepository.save(loan);

        // Write AuditLog.
        AuditLog auditLog = AuditLog.builder()
                .loanApplication(updatedLoan)
                .changedById(userId)
                .fromStatus(LoanStatus.ASSESSED.toString())
                .toStatus(newStatus.toString())
                .notes("Underwriting decision: " + request.getDecision())
                .build();

        auditLogRepository.save(auditLog);

        // Publish event to Kafka.
        publishLoanStatusEvent(LoanStatus.ASSESSED, newStatus, userDto, updatedLoan.getId());

        return new LoanApplicationDTO(
                updatedLoan.getId(),
                updatedLoan.getLoanAmount(),
                updatedLoan.getPropertyAddress(),
                updatedLoan.getStatus(),
                updatedLoan.getCreatedAt()
        );
    }

    // LEGAL uploads a document reference for a loan in LEGAL_REVIEW state.
    // Creates LoanDocument record but does not change loan status.
    // Throws IllegalArgumentException if loan not found, not in LEGAL_REVIEW, or user is not LEGAL.
    public LoanApplicationDTO uploadDocument(Long loanId, Long userId, String userRole, LoanDocumentRequest request) {
        if (!"LEGAL".equals(userRole)) {
            throw new IllegalArgumentException("Only LEGAL can upload documents");
        }

        LoanApplication loan = loanApplicationRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getStatus() != LoanStatus.LEGAL_REVIEW) {
            throw new IllegalArgumentException("Loan must be in LEGAL_REVIEW status to upload documents");
        }

        // Create LoanDocument (no status change).
        LoanDocument document = LoanDocument.builder()
                .loanApplication(loan)
                .uploadedById(userId)
                .documentType(request.getDocumentType())
                .filePath(request.getFilePath())
                .build();

        loanDocumentRepository.save(document);

        // Return the loan without changes (status remains LEGAL_REVIEW).
        return new LoanApplicationDTO(
                loan.getId(),
                loan.getLoanAmount(),
                loan.getPropertyAddress(),
                loan.getStatus(),
                loan.getCreatedAt()
        );
    }

    // DISBURSEMENT moves a loan from LEGAL_REVIEW to DISBURSED and writes AuditLog.
    // Throws IllegalArgumentException if loan not found, not in LEGAL_REVIEW, or user is not DISBURSEMENT.
    public LoanApplicationDTO disburseLoan(Long loanId, Long userId, String userRole) {
        if (!"DISBURSEMENT".equals(userRole)) {
            throw new IllegalArgumentException("Only DISBURSEMENT can disburse loans");
        }

        LoanApplication loan = loanApplicationRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getStatus() != LoanStatus.LEGAL_REVIEW) {
            throw new IllegalArgumentException("Loan must be in LEGAL_REVIEW status to disburse");
        }

        // Fetch user from auth-service.
        UserDto userDto = userClient.getUserById(userId);

        // Transition loan to DISBURSED.
        loan.setStatus(LoanStatus.DISBURSED);
        LoanApplication updatedLoan = loanApplicationRepository.save(loan);

        // Write AuditLog.
        AuditLog auditLog = AuditLog.builder()
                .loanApplication(updatedLoan)
                .changedById(userId)
                .fromStatus(LoanStatus.LEGAL_REVIEW.toString())
                .toStatus(LoanStatus.DISBURSED.toString())
                .notes("Loan disbursed")
                .build();

        auditLogRepository.save(auditLog);

        // Publish event to Kafka.
        publishLoanStatusEvent(LoanStatus.LEGAL_REVIEW, LoanStatus.DISBURSED, userDto, updatedLoan.getId());

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

        // Fetch user from auth-service.
        UserDto userDto = userClient.getUserById(userId);

        // Write AuditLog entry.
        AuditLog auditLog = AuditLog.builder()
                .loanApplication(updatedLoan)
                .changedById(userId)
                .fromStatus(fromStatus.toString())
                .toStatus(toStatus.toString())
                .notes("Status changed from " + fromStatus + " to " + toStatus)
                .build();

        auditLogRepository.save(auditLog);

        // Publish event to Kafka.
        publishLoanStatusEvent(fromStatus, toStatus, userDto, updatedLoan.getId());

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
                return loan.getBorrowerId().equals(userId);
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

    // Helper: Publish a LoanStatusEvent to Kafka after status transition.
    private void publishLoanStatusEvent(LoanStatus fromStatus, LoanStatus toStatus, UserDto changedByUser, Long loanId) {
        LoanStatusEvent event = new LoanStatusEvent();
        event.setLoanId(loanId);
        event.setFromStatus(fromStatus.toString());
        event.setToStatus(toStatus.toString());
        event.setChangedBy(changedByUser.getEmail());
        event.setTimestamp(LocalDateTime.now());
        event.setNotifyRole(computeNotifyRole(toStatus));

        kafkaProducerService.publish(event);
    }

    // Helper: Compute which role should be notified based on the new status.
    // Maps toStatus → notifyRole per the spec state machine.
    private String computeNotifyRole(LoanStatus toStatus) {
        switch (toStatus) {
            case UNDER_REVIEW:
                return "CREDIT_OFFICER";
            case ASSESSED:
                return "APPRAISER";
            case APPROVED:
            case REJECTED:
                return "UNDERWRITER";
            case LEGAL_REVIEW:
                return "LEGAL";
            case DISBURSED:
                return "DISBURSEMENT";
            default:
                return null;
        }
    }
}
