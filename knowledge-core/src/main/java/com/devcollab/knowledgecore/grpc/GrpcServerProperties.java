package com.devcollab.knowledgecore.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "devcollab.grpc.server")
public record GrpcServerProperties(
        boolean enabled,
        int port,
        int maxInboundMessageSize,
        Duration gracefulShutdownTimeout
) {
    public GrpcServerProperties {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("gRPC port is invalid");
        }
        if (maxInboundMessageSize < 1) {
            throw new IllegalArgumentException(
                    "gRPC max inbound message size must be positive"
            );
        }
        if (gracefulShutdownTimeout == null
                || gracefulShutdownTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "gRPC graceful shutdown timeout is invalid"
            );
        }
    }
}
