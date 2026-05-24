package com.loanorigination.loanservice.dto;

import com.loanorigination.loanservice.enums.LoanStatus;
import java.math.BigDecimal;

// Lightweight DTO for listing loans.
// Contains just the essential info: id, amount, status, borrower name.
public class LoanSummaryDTO {

    private Long id;
    private BigDecimal loanAmount;
    private String borrowerName;
    private LoanStatus status;

    // Constructors
    public LoanSummaryDTO() {
    }

    public LoanSummaryDTO(Long id, BigDecimal loanAmount, String borrowerName, LoanStatus status) {
        this.id = id;
        this.loanAmount = loanAmount;
        this.borrowerName = borrowerName;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(BigDecimal loanAmount) {
        this.loanAmount = loanAmount;
    }

    public String getBorrowerName() {
        return borrowerName;
    }

    public void setBorrowerName(String borrowerName) {
        this.borrowerName = borrowerName;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }
}
