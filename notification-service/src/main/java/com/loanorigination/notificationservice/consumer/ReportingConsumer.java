package com.loanorigination.notificationservice.consumer;

import com.loanorigination.notificationservice.config.KafkaConsumerConfig;
import com.loanorigination.notificationservice.entity.LoanStatusEventEntity;
import com.loanorigination.notificationservice.event.LoanStatusEvent;
import com.loanorigination.notificationservice.repository.ReportingEventRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ReportingConsumer {
    private final ReportingEventRepository repository;

    public ReportingConsumer(ReportingEventRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
        topics = "loan-status-changes",
        groupId = KafkaConsumerConfig.REPORTING_GROUP,
        containerFactory = "reportingKafkaListenerContainerFactory"
    )
    public void consumeEvent(LoanStatusEvent event) {
        repository.findByLoanIdAndToStatus(event.getLoanId(), event.getToStatus())
            .ifPresentOrElse(
                existing -> {},
                () -> {
                    LoanStatusEventEntity entity = new LoanStatusEventEntity();
                    entity.setLoanId(event.getLoanId());
                    entity.setFromStatus(event.getFromStatus());
                    entity.setToStatus(event.getToStatus());
                    entity.setChangedBy(event.getChangedBy());
                    entity.setReceivedAt(LocalDateTime.now());
                    repository.save(entity);
                }
            );
    }
}
