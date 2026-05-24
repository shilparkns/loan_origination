package com.loanorigination.notificationservice.config;

import com.loanorigination.notificationservice.event.LoanStatusEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

// Kafka consumer configuration for notification-service.
// Sets up two consumer groups: reporting-group and notification-group.
// Both listen to loan-status-changes topic independently.
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    public static final String REPORTING_GROUP = "reporting-group";
    public static final String NOTIFICATION_GROUP = "notification-group";

    // Consumer factory for both groups (shared configuration).
    @Bean
    public ConsumerFactory<String, LoanStatusEvent> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, LoanStatusEvent.class.getName());
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    // Container factory for reporting-group.
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LoanStatusEvent> reportingKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, LoanStatusEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    // Container factory for notification-group.
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LoanStatusEvent> notificationKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, LoanStatusEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
