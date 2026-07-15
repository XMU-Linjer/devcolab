package com.devcollab.worker.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableConfigurationProperties(WorkerKafkaProperties.class)
public class WorkerKafkaErrorHandlingConfig {

    private static final Logger log =
            LoggerFactory.getLogger(WorkerKafkaErrorHandlingConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            WorkerKafkaProperties properties
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) -> dlqDestination(
                                record,
                                exception,
                                properties
                        )
                );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(
                        properties.getRetry().getInterval().toMillis(),
                        properties.getRetry().getMaxAttempts()
                )
        );
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) ->
                log.warn(
                        "Kafka consumer retrying record topic={} partition={} offset={} attempt={} error={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        deliveryAttempt,
                        exception.toString()
                )
        );
        return errorHandler;
    }

    private TopicPartition dlqDestination(
            ConsumerRecord<?, ?> record,
            Exception exception,
            WorkerKafkaProperties properties
    ) {
        log.error(
                "Kafka consumer sending record to DLQ topic={} sourceTopic={} partition={} offset={} key={} error={}",
                properties.getDlqTopic(),
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                exception.toString()
        );
        return new TopicPartition(properties.getDlqTopic(), record.partition());
    }
}
