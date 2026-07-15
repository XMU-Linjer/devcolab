package com.devcollab.worker.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "devcollab.worker.kafka")
public class WorkerKafkaProperties {

    private String topic = "devcollab.domain-events";
    private String dlqTopic = "devcollab.domain-events.dlq";
    private Retry retry = new Retry();

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDlqTopic() {
        return dlqTopic;
    }

    public void setDlqTopic(String dlqTopic) {
        this.dlqTopic = dlqTopic;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public static class Retry {

        private Duration interval = Duration.ofSeconds(1);
        private long maxAttempts = 2;

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }

        public long getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(long maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }
}
