package com.loanorigination.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Mirror of LoanStatusEvent from loan-service.
// Deserializes Kafka messages. Owned independently — no shared library.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanStatusEvent {
    private Long loanId;
    private String fromStatus;
    private String toStatus;
    private String changedBy;
    private LocalDateTime timestamp;
    private String notifyRole;
}
