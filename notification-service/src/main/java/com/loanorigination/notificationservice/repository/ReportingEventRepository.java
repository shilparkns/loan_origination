package com.loanorigination.notificationservice.repository;

import com.loanorigination.notificationservice.entity.LoanStatusEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportingEventRepository extends JpaRepository<LoanStatusEventEntity, Long> {
    @Query("SELECT e FROM LoanStatusEventEntity e WHERE e.loanId = :loanId AND e.toStatus = :toStatus")
    Optional<LoanStatusEventEntity> findByLoanIdAndToStatus(@Param("loanId") Long loanId, @Param("toStatus") String toStatus);
}
