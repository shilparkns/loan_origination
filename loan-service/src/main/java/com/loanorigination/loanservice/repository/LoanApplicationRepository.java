package com.loanorigination.loanservice.repository;

import com.loanorigination.loanservice.entity.LoanApplication;
import com.loanorigination.loanservice.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {
    List<LoanApplication> findByStatus(LoanStatus status);
    List<LoanApplication> findByBorrowerId(Long borrowerId);
}

