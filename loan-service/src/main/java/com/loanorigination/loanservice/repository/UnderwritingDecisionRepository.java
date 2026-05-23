package com.loanorigination.loanservice.repository;

import com.loanorigination.loanservice.entity.UnderwritingDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UnderwritingDecisionRepository extends JpaRepository<UnderwritingDecision, Long> {
    Optional<UnderwritingDecision> findByLoanApplicationId(Long loanApplicationId);
}
