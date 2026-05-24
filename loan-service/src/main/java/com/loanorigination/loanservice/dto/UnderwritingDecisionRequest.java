package com.loanorigination.loanservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingDecisionRequest {
    @NotBlank(message = "Decision is required")
    @Pattern(regexp = "APPROVED|REJECTED", message = "Decision must be APPROVED or REJECTED")
    private String decision;

    private String notes;
}
