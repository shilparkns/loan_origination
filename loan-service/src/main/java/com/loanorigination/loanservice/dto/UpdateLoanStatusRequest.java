package com.loanorigination.loanservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLoanStatusRequest {
    @NotNull(message = "Status is required")
    private String toStatus;
}
