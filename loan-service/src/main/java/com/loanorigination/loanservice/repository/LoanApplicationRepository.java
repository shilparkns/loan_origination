package com.loanorigination.loanservice.repository;

import com.loanorigination.loanservice.entity.LoanApplication;
import com.loanorigination.loanservice.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {
    List<LoanApplication> findByStatus(LoanStatus status);
    List<LoanApplication> findByBorrowerId(Long borrowerId);

    @Query("SELECT l FROM LoanApplication l JOIN FETCH l.borrower WHERE l.status = :status")
    List<LoanApplication> findByStatusWithBorrower(@Param("status") LoanStatus status);

    @Query("SELECT l FROM LoanApplication l JOIN FETCH l.borrower WHERE l.borrower.id = :borrowerId")
    List<LoanApplication> findByBorrowerIdWithBorrower(@Param("borrowerId") Long borrowerId);
}
