package com.loanorigination.loanservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanDocumentRequest {
    @NotBlank(message = "Document type is required")
    private String documentType;

    @NotBlank(message = "File path is required")
    private String filePath;
}
