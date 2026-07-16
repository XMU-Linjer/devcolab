package com.devcollab.worker.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "devcollab.worker.kafka")
public class WorkerKafkaProperties {

    private String documentTopic = "devcollab.document.events";
    private String cacheTopic = "devcollab.cache.events";
    private String reviewTopic = "devcollab.review.events";
    private String notificationTopic = "devcollab.notification.events";
    private String dlqTopic = "devcollab.dead-letter";
    private Retry retry = new Retry();

    public String getDocumentTopic() {
        return documentTopic;
    }

    public void setDocumentTopic(String documentTopic) {
        this.documentTopic = documentTopic;
    }

    public String getCacheTopic() {
        return cacheTopic;
    }

    public void setCacheTopic(String cacheTopic) {
        this.cacheTopic = cacheTopic;
    }

    public String getReviewTopic() {
        return reviewTopic;
    }

    public void setReviewTopic(String reviewTopic) {
        this.reviewTopic = reviewTopic;
    }

    public String getNotificationTopic() {
        return notificationTopic;
    }

    public void setNotificationTopic(String notificationTopic) {
        this.notificationTopic = notificationTopic;
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
