package com.devcollab.worker.kafka;

import com.devcollab.worker.consumerinbox.ConsumerInboxRepository;
import com.devcollab.worker.git.GitRepositoryStorageService;
import com.devcollab.worker.observability.WorkerEventMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitRepositoryConsumerTests {

    @Test
    void doesNotMarkDeleteConsumedWhenDirectoryCleanupFails()
            throws Exception {
        ConsumerInboxRepository inbox = mock(ConsumerInboxRepository.class);
        GitRepositoryStorageService storage =
                mock(GitRepositoryStorageService.class);
        WorkerEventMetrics metrics = mock(WorkerEventMetrics.class);
        GitRepositoryConsumer consumer =
                new GitRepositoryConsumer(inbox, storage, metrics);
        UUID eventId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        when(inbox.hasConsumed("git-repository", eventId))
                .thenReturn(false);
        org.mockito.Mockito.doThrow(
                new IllegalStateException("directory cleanup failed")
        ).when(storage).delete(workspaceId, repositoryId);

        String message = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(new KafkaOutboxMessage(
                        eventId,
                        "GIT_REPOSITORY",
                        repositoryId,
                        "GIT_REPOSITORY_DELETE_REQUESTED",
                        new ObjectMapper().writeValueAsString(Map.of(
                                "workspaceId", workspaceId.toString(),
                                "repositoryId", repositoryId.toString()
                        )),
                        Instant.now()
                ));

        assertThatThrownBy(() -> consumer.onEvent(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("directory cleanup failed");
        verify(inbox, never()).markConsumed(any(), eq(eventId));
        verify(metrics).projectionFailed(
                "git-repository",
                "GIT_REPOSITORY_DELETE_REQUESTED"
        );
    }
}
