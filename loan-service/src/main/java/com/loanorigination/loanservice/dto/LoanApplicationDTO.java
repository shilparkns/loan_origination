package com.loanorigination.loanservice.dto;

import com.loanorigination.loanservice.enums.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Response payload for POST /api/loans (and other loan endpoints).
// Returns the created/retrieved LoanApplication with key details.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationDTO {
    private Long id;
    private BigDecimal loanAmount;
    private String propertyAddress;
    private LoanStatus status;
    private LocalDateTime createdAt;
}
