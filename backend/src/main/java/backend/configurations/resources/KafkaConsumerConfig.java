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
 * Configures a dedicated {@link ConcurrentKafkaListenerContainerFactory} for the email
 * consumer. After 3 delivery attempts the message is routed to {@code email-events.dlq}
 * via {@link DeadLetterPublishingRecoverer} so transient mail-service outages do not
 * permanently lose transactional email events.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final int EMAIL_MAX_ATTEMPTS = 3;
    private static final long EMAIL_RETRY_INTERVAL_MS = 2_000L;

    @Value("${app.kafka.topics.email-events.dlq}")
    private String emailDlqTopic;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> emailKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, ex) -> new org.apache.kafka.common.TopicPartition(emailDlqTopic, 0));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(EMAIL_RETRY_INTERVAL_MS, EMAIL_MAX_ATTEMPTS - 1L));

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
