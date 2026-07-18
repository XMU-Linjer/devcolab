package com.devcollab.gateway.collaboration;

import com.devcollab.protocol.core.v1.VerifyDocumentAccessRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(
        prefix = "devcollab.gateway",
        name = "core-transport",
        havingValue = "grpc",
        matchIfMissing = true
)
public class GrpcCoreDocumentAccessVerifier
        implements CoreDocumentAccessVerifier {

    private final CoreGrpcChannel channel;
    private final CoreGrpcClientProperties properties;
    private final CoreGrpcClientMetrics metrics;

    public GrpcCoreDocumentAccessVerifier(
            CoreGrpcChannel channel,
            CoreGrpcClientProperties properties,
            CoreGrpcClientMetrics metrics
    ) {
        this.channel = channel;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void verifyCanAccess(UUID documentId, String accessToken) {
        metrics.record("VerifyDocumentAccess", () ->
                GrpcCoreClientSupport.authenticatedStub(
                                channel,
                                properties.deadline(),
                                accessToken
                        )
                        .verifyDocumentAccess(
                                VerifyDocumentAccessRequest.newBuilder()
                                        .setDocumentId(documentId.toString())
                                        .build()
                ));
    }
}
