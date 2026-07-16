package com.devcollab.gateway.collaboration;

import com.devcollab.gateway.collaboration.CoreBlockOperationClient.CoreBlockUpdateResult;
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

class CoreBlockOperationClientTests {

    @Test
    void updateTextMapsSuccessfulCoreResponseToApplied() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        CoreBlockOperationClient client = clientWith(
                captured,
                HttpStatus.OK,
                """
                {
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
                """.formatted(blockId, documentId, UUID.randomUUID())
        );

        CoreBlockUpdateResult result = client.updateText(
                documentId,
                blockId,
                "access-token",
                "new text",
                1
        );

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.block().id()).isEqualTo(blockId);
        assertThat(result.block().version()).isEqualTo(2);
        assertThat(captured.get().method().name()).isEqualTo("PATCH");
        assertThat(captured.get().url().getPath())
                .isEqualTo("/api/v1/documents/%s/blocks/%s".formatted(
                        documentId,
                        blockId
                ));
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer access-token");
    }

    @Test
    void updateTextMapsCoreConflictToConflictResult() {
        CoreBlockOperationClient client = clientWith(
                new AtomicReference<>(),
                HttpStatus.CONFLICT,
                "{}"
        );

        CoreBlockUpdateResult result = client.updateText(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "access-token",
                "new text",
                1
        );

        assertThat(result.status()).isEqualTo("CONFLICT");
        assertThat(result.message()).contains("version conflict");
    }

    private CoreBlockOperationClient clientWith(
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
        return new CoreBlockOperationClient(
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
