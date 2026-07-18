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

    public GrpcCoreDocumentAccessVerifier(
            CoreGrpcChannel channel,
            CoreGrpcClientProperties properties
    ) {
        this.channel = channel;
        this.properties = properties;
    }

    @Override
    public void verifyCanAccess(UUID documentId, String accessToken) {
        GrpcCoreClientSupport.authenticatedStub(
                        channel,
                        properties.deadline(),
                        accessToken
                )
                .verifyDocumentAccess(
                        VerifyDocumentAccessRequest.newBuilder()
                                .setDocumentId(documentId.toString())
                                .build()
                );
    }
}
