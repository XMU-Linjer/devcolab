package com.devcollab.gateway.collaboration;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;

@Component
public class CoreDocumentAccessVerifier {

    private final WebClient webClient;

    public CoreDocumentAccessVerifier(
            WebClient.Builder builder,
            GatewayProperties properties
    ) {
        this.webClient = builder
                .baseUrl(properties.coreBaseUrl())
                .build();
    }

    public void verifyCanAccess(UUID documentId, String accessToken) {
        webClient.get()
                .uri("/api/v1/documents/{documentId}", documentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(3));
    }
}
