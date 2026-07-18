package com.devcollab.gateway.collaboration;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(
        prefix = "devcollab.gateway",
        name = "core-transport",
        havingValue = "grpc",
        matchIfMissing = true
)
public class CoreGrpcChannel implements AutoCloseable {

    private static final Logger log =
            LoggerFactory.getLogger(CoreGrpcChannel.class);

    private final CoreGrpcClientProperties properties;
    private final ManagedChannel channel;

    public CoreGrpcChannel(CoreGrpcClientProperties properties) {
        this.properties = properties;
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(
                        properties.host(),
                        properties.port()
                )
                .maxInboundMessageSize(properties.maxInboundMessageSize());
        if (properties.plaintext()) {
            builder.usePlaintext();
        } else {
            builder.useTransportSecurity();
        }
        this.channel = builder.build();
        log.info(
                "Core gRPC channel configured target={}:{} plaintext={}",
                properties.host(),
                properties.port(),
                properties.plaintext()
        );
    }

    public ManagedChannel channel() {
        return channel;
    }

    public ConnectivityState state() {
        return channel.getState(false);
    }

    @Override
    @PreDestroy
    public void close() {
        if (channel.isShutdown()) {
            return;
        }
        channel.shutdown();
        try {
            if (!channel.awaitTermination(
                    properties.shutdownTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            )) {
                channel.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
        log.info("Core gRPC channel stopped");
    }
}
