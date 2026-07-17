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

        CoreDocumentOperationResult result = client.updateText(
                documentId,
                blockId,
                operationId,
                "access-token",
                "new text",
                1
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

        CoreDocumentOperationResult result = client.updateText(
                documentId,
                blockId,
                operationId,
                "access-token",
                "saved",
                0
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

        CoreDocumentOperationResult result = client.updateText(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "access-token",
                "new text",
                1
        );

        assertThat(result.status()).isEqualTo("CONFLICT");
        assertThat(result.message()).contains("conflict");
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
