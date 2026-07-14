package com.devcollab.knowledgecore.search.projection;

import com.devcollab.knowledgecore.common.outbox.application.OutboxEventHandler;
import com.devcollab.knowledgecore.common.outbox.domain.OutboxEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "devcollab.search.elasticsearch.enabled",
        havingValue = "true"
)
public class SearchProjectionOutboxEventHandler implements OutboxEventHandler {

    private final SearchIndexProjectionService projectionService;

    public SearchProjectionOutboxEventHandler(
            SearchIndexProjectionService projectionService
    ) {
        this.projectionService = projectionService;
    }

    @Override
    public void handle(OutboxEvent event) {
        projectionService.project(event);
    }
}
