package com.loanorigination.loanservice.repository;

import com.loanorigination.loanservice.entity.User;
import com.loanorigination.loanservice.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
