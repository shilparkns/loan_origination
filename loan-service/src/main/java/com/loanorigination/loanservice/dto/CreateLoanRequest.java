package com.loanorigination.loanservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

// Request payload for POST /api/loans.
// BORROWER submits a new loan application with the loan amount and property address.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateLoanRequest {

    @DecimalMin(value = "0.01", message = "Loan amount must be greater than 0")
    private BigDecimal loanAmount;

    @NotBlank(message = "Property address is required")
    private String propertyAddress;
}
