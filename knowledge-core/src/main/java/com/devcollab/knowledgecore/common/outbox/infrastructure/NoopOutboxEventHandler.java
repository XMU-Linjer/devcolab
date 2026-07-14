package com.devcollab.knowledgecore.common.outbox.infrastructure;

import com.devcollab.knowledgecore.common.outbox.application.OutboxEventHandler;
import com.devcollab.knowledgecore.common.outbox.domain.OutboxEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MVP relay handler.
 *
 * The current stage only proves that pending outbox events can be consumed
 * and marked as published. Later stages can replace this handler with Kafka,
 * Elasticsearch indexing, Git snapshot, notification, or Agent review
 * dispatchers without changing the business write path.
 */
@Component
@ConditionalOnProperty(
        name = "devcollab.search.elasticsearch.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoopOutboxEventHandler implements OutboxEventHandler {

    @Override
    public void handle(OutboxEvent event) {
        // Intentionally no-op for the minimum relay stage.
    }
}
