package com.loanorigination.loanservice.dto;

import com.loanorigination.loanservice.enums.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Rich response DTO for GET /api/loans/{id}.
// Includes loan details, borrower info, assessment, decision, and documents.
@Getter
@Setter
@NoArgsConstructor
public class LoanDetailDTO {

    private Long id;
    private BigDecimal loanAmount;
    private String propertyAddress;
    private LoanStatus status;
    private LocalDateTime createdAt;

    // Borrower info
    private String borrowerFirstName;
    private String borrowerLastName;
    private String borrowerEmail;
    private String borrowerPhone;
    private Integer borrowerCreditScore;
    private BigDecimal borrowerAnnualIncome;

    // Assessment (if exists)
    private BigDecimal assessedValue;
    private LocalDateTime assessedAt;

    // Decision (if exists)
    private String underwritingDecision;
    private String decisionNotes;
    private LocalDateTime decidedAt;

    // Documents (list)
    private List<DocumentSummary> documents;

    // Nested DTO for documents
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentSummary {
        private Long id;
        private String documentPath;
    }
}
