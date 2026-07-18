package com.devcollab.gateway.collaboration;

import com.devcollab.gateway.collaboration.CoreDocumentOperationClient.CoreDocumentOperationResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CoreDocumentOperationClientTests {

    @Test
    void updateTextMapsSuccessfulCoreResponseToAppliedWithSequence() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        CoreDocumentOperationClient client = clientWith(
                captured,
                HttpStatus.OK,
                """
                {
                  "clientOperationId": "%s",
                  "blockId": "%s",
                  "operationType": "UPDATE_TEXT",
                  "status": "APPLIED",
                  "documentSequence": 7,
                  "block": {
                    "id": "%s",
                    "documentId": "%s",
                    "type": "PARAGRAPH",
                    "content": { "text": "new text" },
                    "sortOrder": 0,
                    "version": 2,
                    "createdBy": "%s",
                    "createdAt": "2026-07-15T10:00:00Z",
                    "updatedAt": "2026-07-15T10:01:00Z"
                  }
                }
                """.formatted(
                        operationId,
                        blockId,
                        blockId,
                        documentId,
                        UUID.randomUUID()
                )
        );

        CoreDocumentOperationResult result = client.apply(
                documentId,
                blockId,
                operationId,
                "access-token",
                "UPDATE_TEXT",
                "new text",
                1L,
                null,
                null
        );

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.documentSequence()).isEqualTo(7);
        assertThat(result.block().version()).isEqualTo(2);
        assertThat(captured.get().method().name()).isEqualTo("POST");
        assertThat(captured.get().url().getPath()).isEqualTo(
                "/api/v1/documents/%s/collaboration-operations"
                        .formatted(documentId)
        );
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer access-token");
    }

    @Test
    void updateTextPreservesDuplicateResultAndOriginalBlock() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        CoreDocumentOperationClient client = clientWith(
                new AtomicReference<>(),
                HttpStatus.OK,
                """
                {
                  "clientOperationId": "%s",
                  "blockId": "%s",
                  "operationType": "UPDATE_TEXT",
                  "status": "DUPLICATE",
                  "documentSequence": 3,
                  "block": {
                    "id": "%s", "documentId": "%s", "type": "PARAGRAPH",
                    "content": { "text": "saved" }, "sortOrder": 0,
                    "version": 1, "createdBy": "%s",
                    "createdAt": "2026-07-15T10:00:00Z",
                    "updatedAt": "2026-07-15T10:01:00Z"
                  }
                }
                """.formatted(
                        operationId,
                        blockId,
                        blockId,
                        documentId,
                        UUID.randomUUID()
                )
        );

        CoreDocumentOperationResult result = client.apply(
                documentId,
                blockId,
                operationId,
                "access-token",
                "UPDATE_TEXT",
                "saved",
                0L,
                null,
                null
        );

        assertThat(result.status()).isEqualTo("DUPLICATE");
        assertThat(result.documentSequence()).isEqualTo(3);
        assertThat(result.block().content().text()).isEqualTo("saved");
    }

    @Test
    void updateTextMapsCoreConflictToConflictResult() {
        CoreDocumentOperationClient client = clientWith(
                new AtomicReference<>(),
                HttpStatus.CONFLICT,
                "{}"
        );

        CoreDocumentOperationResult result = client.apply(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "access-token",
                "UPDATE_TEXT",
                "new text",
                1L,
                null,
                null
        );

        assertThat(result.status()).isEqualTo("CONFLICT");
        assertThat(result.message()).contains("conflict");
    }

    @Test
    void catchUpMapsOrderedPageAndForwardsCursorParameters() {
        UUID documentId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        CoreDocumentOperationClient client = clientWith(
                captured,
                HttpStatus.OK,
                """
                {
                  "requestedAfterSequence": 4,
                  "latestDocumentSequence": 6,
                  "hasMore": true,
                  "operations": [{
                    "clientOperationId": "%s",
                    "blockId": "%s",
                    "operationType": "CREATE_BLOCK",
                    "status": "APPLIED",
                    "documentSequence": 5,
                    "operatorUserId": "%s",
                    "block": null,
                    "blocks": []
                  }]
                }
                """.formatted(operationId, blockId, operatorId)
        );

        var result = client.listAfter(
                documentId,
                "access-token",
                4,
                1
        );

        assertThat(result.requestedAfterSequence()).isEqualTo(4);
        assertThat(result.latestDocumentSequence()).isEqualTo(6);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.operations()).hasSize(1);
        assertThat(result.operations().getFirst().documentSequence())
                .isEqualTo(5);
        assertThat(result.operations().getFirst().operatorUserId())
                .isEqualTo(operatorId);
        assertThat(captured.get().method().name()).isEqualTo("GET");
        assertThat(captured.get().url().getQuery())
                .contains("afterSequence=4", "limit=1");
    }

    private CoreDocumentOperationClient clientWith(
            AtomicReference<ClientRequest> captured,
            HttpStatus status,
            String body
    ) {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(status)
                            .header(
                                    HttpHeaders.CONTENT_TYPE,
                                    MediaType.APPLICATION_JSON_VALUE
                            )
                            .body(body)
                            .build());
                });
        return new CoreDocumentOperationClient(
                builder,
                new GatewayProperties(
                        "http://core.example",
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(5)
                )
        );
    }
}
