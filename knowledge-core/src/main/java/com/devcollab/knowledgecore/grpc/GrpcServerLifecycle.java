package com.devcollab.knowledgecore.grpc;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(
        prefix = "devcollab.grpc.server",
        name = "enabled",
        havingValue = "true"
)
public class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log =
            LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final GrpcServerProperties properties;
    private final BindableService collaborationService;
    private final GrpcJwtAuthenticationInterceptor authenticationInterceptor;
    private volatile Server server;
    private volatile boolean running;

    public GrpcServerLifecycle(
            GrpcServerProperties properties,
            KnowledgeCoreCollaborationGrpcService collaborationService,
            GrpcJwtAuthenticationInterceptor authenticationInterceptor
    ) {
        this.properties = properties;
        this.collaborationService = collaborationService;
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            server = ServerBuilder.forPort(properties.port())
                    .maxInboundMessageSize(properties.maxInboundMessageSize())
                    .addService(ServerInterceptors.intercept(
                            collaborationService,
                            authenticationInterceptor
                    ))
                    .build()
                    .start();
            running = true;
            log.info("Knowledge Core gRPC server started on port {}", server.getPort());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to start Knowledge Core gRPC server",
                    exception
            );
        }
    }

    @Override
    public void stop() {
        Server current = server;
        if (current == null) {
            running = false;
            return;
        }
        current.shutdown();
        try {
            if (!current.awaitTermination(
                    properties.gracefulShutdownTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            )) {
                current.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            current.shutdownNow();
        } finally {
            running = false;
            server = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
