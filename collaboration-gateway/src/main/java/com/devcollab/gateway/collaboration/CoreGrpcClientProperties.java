package com.devcollab.gateway.collaboration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "devcollab.gateway.core-grpc")
public record CoreGrpcClientProperties(
        String host,
        int port,
        boolean plaintext,
        Duration deadline,
        int maxInboundMessageSize,
        Duration shutdownTimeout
) {
    public CoreGrpcClientProperties {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Core gRPC host is required");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Core gRPC port is invalid");
        }
        if (deadline == null || deadline.isZero() || deadline.isNegative()) {
            throw new IllegalArgumentException(
                    "Core gRPC deadline must be positive"
            );
        }
        if (maxInboundMessageSize < 1) {
            throw new IllegalArgumentException(
                    "Core gRPC max inbound message size must be positive"
            );
        }
        if (shutdownTimeout == null || shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "Core gRPC shutdown timeout is invalid"
            );
        }
    }
}
