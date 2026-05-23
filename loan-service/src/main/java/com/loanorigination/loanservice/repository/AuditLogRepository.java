package com.loanorigination.loanservice.repository;

import com.loanorigination.loanservice.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByLoanApplicationIdOrderByChangedAtAsc(Long loanApplicationId);
}
