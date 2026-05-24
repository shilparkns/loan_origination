package com.loanorigination.loanservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

// Request payload for POST /api/loans.
// BORROWER submits a new loan application with the loan amount and property address.
public class CreateLoanRequest {

    @DecimalMin(value = "0.01", message = "Loan amount must be greater than 0")
    private BigDecimal loanAmount;

    @NotBlank(message = "Property address is required")
    private String propertyAddress;

    // Constructors
    public CreateLoanRequest() {
    }

    public CreateLoanRequest(BigDecimal loanAmount, String propertyAddress) {
        this.loanAmount = loanAmount;
        this.propertyAddress = propertyAddress;
    }

    // Getters and Setters
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
}
