package com.devcollab.gateway.collaboration;

import com.devcollab.gateway.collaboration.CollaborationMessages.CoreBlockContent;
import com.devcollab.gateway.collaboration.CollaborationMessages.CoreBlockResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class CoreDocumentOperationClient {

    private final WebClient webClient;

    public CoreDocumentOperationClient(
            WebClient.Builder builder,
            GatewayProperties properties
    ) {
        this.webClient = builder
                .baseUrl(properties.coreBaseUrl())
                .build();
    }

    public CoreDocumentOperationResult apply(
            UUID documentId,
            UUID blockId,
            UUID clientOperationId,
            String accessToken,
            String operationType,
            String text,
            Long expectedVersion,
            String blockType,
            Integer targetIndex
    ) {
        try {
            CoreOperationResponse response = webClient.post()
                    .uri(
                            "/api/v1/documents/{documentId}/collaboration-operations",
                            documentId
                    )
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .bodyValue(new CoreOperationRequest(
                            clientOperationId,
                            blockId,
                            operationType,
                            expectedVersion,
                            blockType,
                            targetIndex,
                            text == null ? null : new CoreBlockContent(text)
                    ))
                    .retrieve()
                    .bodyToMono(CoreOperationResponse.class)
                    .block(Duration.ofSeconds(3));
            if (response == null) {
                throw new IllegalStateException(
                        "Core returned an empty collaboration operation result"
                );
            }
            return new CoreDocumentOperationResult(
                    response.status(),
                    response.documentSequence(),
                    response.block(),
                    response.blocks() == null ? List.of() : response.blocks(),
                    null
            );
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                return CoreDocumentOperationResult.conflict(
                        "Block version conflict or operation id reuse"
                );
            }
            if (exception.getStatusCode().is4xxClientError()) {
                return CoreDocumentOperationResult.rejected(
                        "Core rejected operation: " + exception.getStatusCode()
                );
            }
            throw exception;
        }
    }

    private record CoreOperationRequest(
            UUID clientOperationId,
            UUID blockId,
            String operationType,
            Long expectedVersion,
            String blockType,
            Integer targetIndex,
            CoreBlockContent content
    ) {
    }

    private record CoreOperationResponse(
            UUID clientOperationId,
            UUID blockId,
            String operationType,
            String status,
            long documentSequence,
            CoreBlockResponse block,
            List<CoreBlockResponse> blocks
    ) {
    }

    public record CoreDocumentOperationResult(
            String status,
            Long documentSequence,
            CoreBlockResponse block,
            List<CoreBlockResponse> blocks,
            String message
    ) {
        public static CoreDocumentOperationResult conflict(String message) {
            return new CoreDocumentOperationResult(
                    "CONFLICT",
                    null,
                    null,
                    List.of(),
                    message
            );
        }

        public static CoreDocumentOperationResult rejected(String message) {
            return new CoreDocumentOperationResult(
                    "REJECTED",
                    null,
                    null,
                    List.of(),
                    message
            );
        }
    }
}
