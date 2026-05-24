package com.loanorigination.loanservice.dto;

import com.loanorigination.loanservice.enums.LoanStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Response payload for POST /api/loans (and other loan endpoints).
// Returns the created/retrieved LoanApplication with key details.
public class LoanApplicationDTO {

    private Long id;
    private BigDecimal loanAmount;
    private String propertyAddress;
    private LoanStatus status;
    private LocalDateTime createdAt;

    // Constructors
    public LoanApplicationDTO() {
    }

    public LoanApplicationDTO(Long id, BigDecimal loanAmount, String propertyAddress, LoanStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.loanAmount = loanAmount;
        this.propertyAddress = propertyAddress;
        this.status = status;
        this.createdAt = createdAt;
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

    public String getPropertyAddress() {
        return propertyAddress;
    }

    public void setPropertyAddress(String propertyAddress) {
        this.propertyAddress = propertyAddress;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
