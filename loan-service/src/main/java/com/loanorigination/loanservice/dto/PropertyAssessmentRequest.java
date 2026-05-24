package com.loanorigination.loanservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertyAssessmentRequest {
    @NotNull(message = "Assessed value is required")
    @DecimalMin(value = "0.01", message = "Assessed value must be greater than 0")
    private BigDecimal assessedValue;
}
