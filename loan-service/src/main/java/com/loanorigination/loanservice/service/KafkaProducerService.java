package com.loanorigination.loanservice.service;

import com.loanorigination.loanservice.config.KafkaProducerConfig;
import com.loanorigination.loanservice.event.LoanStatusEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

// Service to publish loan status change events to Kafka.
// Called by LoanService after every successful status transition.
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // Publishes a LoanStatusEvent to the loan-status-changes topic.
    // Key: loan ID (ensures all events for same loan go to same partition, preserving order)
    // Value: full event as JSON
    public void publish(LoanStatusEvent event) {
        kafkaTemplate.send(
                KafkaProducerConfig.LOAN_STATUS_CHANGES_TOPIC,
                String.valueOf(event.getLoanId()),
                event
        );
    }
}
