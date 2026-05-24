package com.loanorigination.notificationservice.consumer;

import com.loanorigination.notificationservice.config.KafkaConsumerConfig;
import com.loanorigination.notificationservice.entity.NotificationLogEntity;
import com.loanorigination.notificationservice.event.LoanStatusEvent;
import com.loanorigination.notificationservice.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationConsumer {
    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationLogRepository repository;

    public NotificationConsumer(NotificationLogRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
        topics = "loan-status-changes",
        groupId = KafkaConsumerConfig.NOTIFICATION_GROUP,
        containerFactory = "notificationKafkaListenerContainerFactory"
    )
    public void consumeEvent(LoanStatusEvent event) {
        String notifyRole = event.getNotifyRole();

        logger.info("[NOTIFY] {} team notified for loan {}", notifyRole, event.getLoanId());

        repository.findByLoanIdAndNotifiedRole(event.getLoanId(), notifyRole)
            .ifPresentOrElse(
                existing -> {},
                () -> {
                    NotificationLogEntity entity = new NotificationLogEntity();
                    entity.setLoanId(event.getLoanId());
                    entity.setNotifiedRole(notifyRole);
                    entity.setMessage(String.format("Loan %d transitioned from %s to %s",
                        event.getLoanId(), event.getFromStatus(), event.getToStatus()));
                    entity.setNotifiedAt(LocalDateTime.now());
                    repository.save(entity);
                }
            );
    }
}
