package com.loanorigination.loanservice.repository;

import com.loanorigination.loanservice.entity.Borrower;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BorrowerRepository extends JpaRepository<Borrower, Long> {
    Optional<Borrower> findByUserId(Long userId);
}
