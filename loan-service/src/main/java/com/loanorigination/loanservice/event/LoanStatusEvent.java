package com.loanorigination.loanservice.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Event published to Kafka whenever a loan status changes.
// Contains all info needed by downstream consumers (notification-service, reporting, etc.)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanStatusEvent {
    private Long loanId;
    private String fromStatus;
    private String toStatus;
    private String changedBy;       // User email (who made the change)
    private LocalDateTime timestamp;
    private String notifyRole;      // Which role should be notified about this change
}
