package com.devcollab.gateway.collaboration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayOperationDeduplicatorTests {

    private final GatewayOperationDeduplicator deduplicator =
            new GatewayOperationDeduplicator();

    @Test
    void firstOperationIsAcceptedAndDuplicateIsRejected() {
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isTrue();

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isFalse();
    }

    @Test
    void sameOperationIdInDifferentDocumentIsIndependent() {
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        assertThat(deduplicator.markFirstSeen(
                UUID.randomUUID(),
                userId,
                operationId
        )).isTrue();

        assertThat(deduplicator.markFirstSeen(
                UUID.randomUUID(),
                userId,
                operationId
        )).isTrue();
    }

    @Test
    void forgottenOperationCanBeRetried() {
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isTrue();

        deduplicator.forget(documentId, userId, operationId);

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isTrue();
    }
}
