package com.loanorigination.notificationservice.repository;

import com.loanorigination.notificationservice.entity.NotificationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, Long> {
    @Query("SELECT n FROM NotificationLogEntity n WHERE n.loanId = :loanId AND n.notifiedRole = :notifiedRole")
    Optional<NotificationLogEntity> findByLoanIdAndNotifiedRole(@Param("loanId") Long loanId, @Param("notifiedRole") String notifiedRole);
}
