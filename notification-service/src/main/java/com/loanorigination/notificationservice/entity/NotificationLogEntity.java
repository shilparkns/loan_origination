package com.loanorigination.notificationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "notified_role", nullable = false, length = 50)
    private String notifiedRole;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;
}
