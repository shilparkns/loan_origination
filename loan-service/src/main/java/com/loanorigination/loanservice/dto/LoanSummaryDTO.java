package com.loanorigination.loanservice.dto;

import com.loanorigination.loanservice.enums.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

// Lightweight DTO for listing loans.
// Contains just the essential info: id, amount, status, borrower name.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanSummaryDTO {
    private Long id;
    private BigDecimal loanAmount;
    private String borrowerName;
    private LoanStatus status;
}
