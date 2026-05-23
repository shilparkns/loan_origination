package com.loanorigination.loanservice.repository;

import com.loanorigination.loanservice.entity.LoanDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanDocumentRepository extends JpaRepository<LoanDocument, Long> {
    List<LoanDocument> findByLoanApplicationId(Long loanApplicationId);
}
