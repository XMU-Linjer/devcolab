package com.devcollab.worker.kafka;

import com.devcollab.worker.consumerinbox.ConsumerInboxRepository;
import com.devcollab.worker.git.GitRepositoryStorageService;
import com.devcollab.worker.observability.WorkerEventMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "devcollab.worker.git.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class GitRepositoryConsumer {

    private static final String CONSUMER_NAME = "git-repository";
    private static final String SYNC_REQUESTED =
            "GIT_REPOSITORY_SYNC_REQUESTED";
    private static final String DELETE_REQUESTED =
            "GIT_REPOSITORY_DELETE_REQUESTED";

    private final ConsumerInboxRepository inboxRepository;
    private final GitRepositoryStorageService storageService;
    private final WorkerEventMetrics metrics;
    private final ObjectMapper objectMapper;

    public GitRepositoryConsumer(
            ConsumerInboxRepository inboxRepository,
            GitRepositoryStorageService storageService,
            WorkerEventMetrics metrics
    ) {
        this.inboxRepository = inboxRepository;
        this.storageService = storageService;
        this.metrics = metrics;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @KafkaListener(
            topics = "${devcollab.worker.kafka.git-topic:devcollab.git.events}",
            groupId = "${devcollab.worker.git.group-id:devcollab-git-repository}"
    )
    public void onEvent(String message) {
        KafkaOutboxMessage event;
        JsonNode payload;
        try {
            event = objectMapper.readValue(message, KafkaOutboxMessage.class);
            payload = objectMapper.readTree(event.payload());
        } catch (Exception exception) {
            metrics.messageMalformed(CONSUMER_NAME);
            return;
        }
        if (!SYNC_REQUESTED.equals(event.eventType())
                && !DELETE_REQUESTED.equals(event.eventType())) {
            return;
        }
        if (inboxRepository.hasConsumed(CONSUMER_NAME, event.eventId())) {
            metrics.duplicateSkipped(CONSUMER_NAME, event.eventType());
            return;
        }

        UUID workspaceId = UUID.fromString(required(payload, "workspaceId"));
        UUID repositoryId = UUID.fromString(required(payload, "repositoryId"));
        try {
            if (SYNC_REQUESTED.equals(event.eventType())) {
                storageService.synchronize(
                        workspaceId,
                        repositoryId,
                        required(payload, "remoteUrl"),
                        required(payload, "defaultBranch")
                );
            } else {
                storageService.delete(workspaceId, repositoryId);
            }
        } catch (RuntimeException exception) {
            metrics.projectionFailed(CONSUMER_NAME, event.eventType());
            throw exception;
        }
        inboxRepository.markConsumed(CONSUMER_NAME, event.eventId());
        metrics.projectionSucceeded(CONSUMER_NAME, event.eventType());
    }

    private String required(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing Git event field: " + field);
        }
        return value.asText();
    }
}
