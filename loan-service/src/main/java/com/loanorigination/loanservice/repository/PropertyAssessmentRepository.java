package com.loanorigination.loanservice.repository;

import com.loanorigination.loanservice.entity.PropertyAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PropertyAssessmentRepository extends JpaRepository<PropertyAssessment, Long> {
    Optional<PropertyAssessment> findByLoanApplicationId(Long loanApplicationId);
}
