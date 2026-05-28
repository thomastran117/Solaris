package backend.configurations.resources;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configures dedicated {@link ConcurrentKafkaListenerContainerFactory} beans for the
 * email and announcement consumers. After 3 delivery attempts each message is routed
 * to its corresponding DLQ topic via {@link DeadLetterPublishingRecoverer}.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_INTERVAL_MS = 2_000L;

    @Value("${app.kafka.topics.email-events.dlq}")
    private String emailDlqTopic;

    @Value("${app.kafka.topics.announcement-events.dlq}")
    private String announcementDlqTopic;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> emailKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, ex) -> new org.apache.kafka.common.TopicPartition(emailDlqTopic, 0));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_ATTEMPTS - 1L));

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> announcementKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, ex) -> new org.apache.kafka.common.TopicPartition(announcementDlqTopic, 0));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_ATTEMPTS - 1L));

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
