package com.devcollab.gateway.collaboration;

import com.devcollab.gateway.collaboration.CollaborationMessages.CoreBlockContent;
import com.devcollab.gateway.collaboration.CollaborationMessages.CoreBlockResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.UUID;

@Component
public class CoreBlockOperationClient {

    private final WebClient webClient;

    public CoreBlockOperationClient(
            WebClient.Builder builder,
            GatewayProperties properties
    ) {
        this.webClient = builder
                .baseUrl(properties.coreBaseUrl())
                .build();
    }

    public CoreBlockUpdateResult updateText(
            UUID documentId,
            UUID blockId,
            String accessToken,
            String text,
            long expectedVersion
    ) {
        try {
            CoreBlockResponse block = webClient.patch()
                    .uri(
                            "/api/v1/documents/{documentId}/blocks/{blockId}",
                            documentId,
                            blockId
                    )
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .bodyValue(new UpdateBlockRequest(
                            new CoreBlockContent(text),
                            expectedVersion
                    ))
                    .retrieve()
                    .bodyToMono(CoreBlockResponse.class)
                    .block(Duration.ofSeconds(3));
            return CoreBlockUpdateResult.applied(block);
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                return CoreBlockUpdateResult.conflict("Block version conflict");
            }
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)
                    || exception.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)
                    || exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
                    || exception.getStatusCode().is4xxClientError()) {
                return CoreBlockUpdateResult.rejected(
                        "Core rejected operation: " + exception.getStatusCode()
                );
            }
            throw exception;
        }
    }

    private record UpdateBlockRequest(
            CoreBlockContent content,
            long expectedVersion
    ) {
    }

    public record CoreBlockUpdateResult(
            String status,
            CoreBlockResponse block,
            String message
    ) {
        public static CoreBlockUpdateResult applied(CoreBlockResponse block) {
            return new CoreBlockUpdateResult("APPLIED", block, null);
        }

        public static CoreBlockUpdateResult conflict(String message) {
            return new CoreBlockUpdateResult("CONFLICT", null, message);
        }

        public static CoreBlockUpdateResult rejected(String message) {
            return new CoreBlockUpdateResult("REJECTED", null, message);
        }
    }
}
