package com.devcollab.gateway.collaboration;

import com.devcollab.gateway.auth.GatewayTokenService;
import com.devcollab.gateway.auth.GatewayTokenService.GatewayUser;
import com.devcollab.gateway.collaboration.CollaborationMessages.ClientMessage;
import com.devcollab.gateway.collaboration.CollaborationMessages.DocumentOperationBroadcast;
import com.devcollab.gateway.collaboration.CollaborationMessages.DocumentOperationResult;
import com.devcollab.gateway.collaboration.CollaborationMessages.ServerMessage;
import com.devcollab.gateway.collaboration.CoreBlockOperationClient.CoreBlockUpdateResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DocumentCollaborationWebSocketHandler implements WebSocketHandler {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentCollaborationWebSocketHandler.class);

    private final GatewayTokenService tokenService;
    private final CoreDocumentAccessVerifier accessVerifier;
    private final CoreBlockOperationClient blockOperationClient;
    private final GatewayOperationDeduplicator operationDeduplicator;
    private final PresenceStore presenceStore;
    private final GatewaySessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public DocumentCollaborationWebSocketHandler(
            GatewayTokenService tokenService,
            CoreDocumentAccessVerifier accessVerifier,
            CoreBlockOperationClient blockOperationClient,
            GatewayOperationDeduplicator operationDeduplicator,
            PresenceStore presenceStore,
            GatewaySessionRegistry sessionRegistry,
            ObjectMapper objectMapper
    ) {
        this.tokenService = tokenService;
        this.accessVerifier = accessVerifier;
        this.blockOperationClient = blockOperationClient;
        this.operationDeduplicator = operationDeduplicator;
        this.presenceStore = presenceStore;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return Mono.fromCallable(() -> authenticate(session))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::handleAuthenticated)
                .onErrorResume(e -> {
                    log.warn("Rejected collaboration websocket: {}", e.getMessage());
                    return session.close(CloseStatus.POLICY_VIOLATION);
                });
    }

    private Mono<Void> handleAuthenticated(ConnectionContext context) {
        sessionRegistry.register(context);
        presenceStore.join(context.documentId(), context.sessionId(), context.user());
        publishPresence(context.documentId());
        send(context, ServerMessage.editing(
                presenceStore.editingStates(context.documentId())
        ));

        Mono<Void> inbound = context.session().receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(message -> handleClientMessage(context, message))
                .doFinally(signalType -> context.outbound().tryEmitComplete())
                .then();

        Mono<Void> outbound = context.session().send(
                context.outbound().asFlux().map(context.session()::textMessage)
        );

        return Mono.when(inbound, outbound)
                .then()
                .doFinally(signalType -> cleanup(context));
    }

    private ConnectionContext authenticate(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        UUID documentId = documentId(uri);
        Map<String, String> query = query(uri);
        UUID workspaceId = requiredUuid(query, "workspaceId");
        String token = required(query, "token");
        GatewayUser user = tokenService.verify(token);
        accessVerifier.verifyCanAccess(documentId, token);

        return new ConnectionContext(
                session,
                Sinks.many().replay().limit(32),
                workspaceId,
                documentId,
                token,
                user
        );
    }

    private Mono<Void> handleClientMessage(
            ConnectionContext context,
            String rawMessage
    ) {
        try {
            ClientMessage message = objectMapper.readValue(
                    rawMessage,
                    ClientMessage.class
            );

            switch (message.type()) {
                case "HEARTBEAT" -> {
                    presenceStore.touch(context.documentId());
                    send(context, ServerMessage.pong());
                }
                case "BLOCK_EDITING_STARTED" -> {
                    requireBlockId(message);
                    broadcastEditing(context, presenceStore.startEditing(
                            context.documentId(),
                            message.blockId(),
                            context.user()
                    ));
                }
                case "BLOCK_EDITING_STOPPED" -> {
                    requireBlockId(message);
                    broadcastEditing(context, presenceStore.stopEditing(
                            context.documentId(),
                            message.blockId()
                    ));
                }
                case "DOCUMENT_OPERATION" -> {
                    return Mono.fromRunnable(() ->
                                    handleDocumentOperation(context, message)
                            )
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(e -> {
                                log.warn(
                                        "Failed to handle document operation: {}",
                                        e.getMessage()
                                );
                                log.debug("Document operation failure detail", e);
                                send(context, ServerMessage.error("文档操作处理失败"));
                                return Mono.empty();
                            })
                            .then();
                }
                default -> send(context, ServerMessage.error(
                        "Unsupported message type: " + message.type()
                ));
            }
        } catch (Exception e) {
            log.warn("Skipping malformed collaboration message");
            log.debug("Malformed collaboration message detail", e);
            send(context, ServerMessage.error("消息格式不正确"));
        }
        return Mono.empty();
    }

    private void handleDocumentOperation(
            ConnectionContext context,
            ClientMessage message
    ) {
        requireOperation(message);
        if (!"UPDATE_TEXT".equals(message.operationType())) {
            send(context, ServerMessage.operationResult(
                    DocumentOperationResult.rejected(
                            message.clientOperationId(),
                            message.blockId(),
                            message.operationType(),
                            "Unsupported operation type: " + message.operationType()
                    )
            ));
            return;
        }

        if (!operationDeduplicator.markFirstSeen(
                context.documentId(),
                context.user().userId(),
                message.clientOperationId()
        )) {
            send(context, ServerMessage.operationResult(
                    DocumentOperationResult.duplicate(
                            message.clientOperationId(),
                            message.blockId(),
                            message.operationType()
                    )
            ));
            return;
        }

        CoreBlockUpdateResult result;
        try {
            result = blockOperationClient.updateText(
                    context.documentId(),
                    message.blockId(),
                    context.accessToken(),
                    message.content().text(),
                    message.expectedVersion()
            );
        } catch (RuntimeException e) {
            operationDeduplicator.forget(
                    context.documentId(),
                    context.user().userId(),
                    message.clientOperationId()
            );
            throw e;
        }

        switch (result.status()) {
            case "APPLIED" -> {
                send(context, ServerMessage.operationResult(
                        DocumentOperationResult.applied(
                                message.clientOperationId(),
                                message.blockId(),
                                message.operationType(),
                                result.block()
                        )
                ));
                broadcastToOthers(
                        context,
                        ServerMessage.operationBroadcast(
                                new DocumentOperationBroadcast(
                                        message.clientOperationId(),
                                        message.blockId(),
                                        message.operationType(),
                                        context.user().userId(),
                                        context.user().username(),
                                        result.block()
                                )
                        )
                );
            }
            case "CONFLICT" -> send(context, ServerMessage.operationResult(
                    DocumentOperationResult.conflict(
                            message.clientOperationId(),
                            message.blockId(),
                            message.operationType(),
                            result.message()
                    )
            ));
            case "REJECTED" -> send(context, ServerMessage.operationResult(
                    DocumentOperationResult.rejected(
                            message.clientOperationId(),
                            message.blockId(),
                            message.operationType(),
                            result.message()
                    )
            ));
            default -> throw new IllegalStateException(
                    "Unknown core operation result: " + result.status()
            );
        }
    }

    private void cleanup(ConnectionContext context) {
        sessionRegistry.unregister(context);
        presenceStore.leave(context.documentId(), context.sessionId());
        publishPresence(context.documentId());
        broadcastEditing(context, presenceStore.stopEditingByUser(
                context.documentId(),
                context.user().userId()
        ));
    }

    private void publishPresence(UUID documentId) {
        sessionRegistry.broadcast(
                documentId,
                write(ServerMessage.presence(presenceStore.members(documentId)))
        );
    }

    private void broadcastEditing(
            ConnectionContext context,
            List<CollaborationMessages.EditingState> editingStates
    ) {
        sessionRegistry.broadcast(
                context.documentId(),
                write(ServerMessage.editing(editingStates))
        );
    }

    private void broadcastToOthers(
            ConnectionContext context,
            ServerMessage message
    ) {
        sessionRegistry.broadcastExcept(
                context.documentId(),
                context.sessionId(),
                write(message)
        );
    }

    private void send(ConnectionContext context, ServerMessage message) {
        context.outbound().tryEmitNext(write(message));
    }

    private String write(ServerMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize websocket message", e);
        }
    }

    private UUID documentId(URI uri) {
        String path = uri.getPath();
        String prefix = "/ws/documents/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            throw new IllegalArgumentException("Missing document id");
        }
        return UUID.fromString(path.substring(prefix.length()));
    }

    private Map<String, String> query(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(rawQuery.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(
                        pair -> decode(pair[0]),
                        pair -> decode(pair[1]),
                        (left, right) -> right
                ));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String required(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing query parameter: " + key);
        }
        return value;
    }

    private UUID requiredUuid(Map<String, String> query, String key) {
        return UUID.fromString(required(query, key));
    }

    private void requireBlockId(ClientMessage message) {
        if (message.blockId() == null) {
            throw new IllegalArgumentException("blockId is required");
        }
    }

    private void requireOperation(ClientMessage message) {
        requireBlockId(message);
        if (message.clientOperationId() == null) {
            throw new IllegalArgumentException("clientOperationId is required");
        }
        if (message.operationType() == null || message.operationType().isBlank()) {
            throw new IllegalArgumentException("operationType is required");
        }
        if (message.expectedVersion() == null || message.expectedVersion() < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion must be zero or greater"
            );
        }
        if (message.content() == null || message.content().text() == null) {
            throw new IllegalArgumentException("content.text is required");
        }
    }
}
