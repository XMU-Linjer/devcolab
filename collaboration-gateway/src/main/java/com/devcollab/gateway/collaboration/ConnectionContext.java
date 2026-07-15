package com.devcollab.gateway.collaboration;

import com.devcollab.gateway.auth.GatewayTokenService.GatewayUser;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Sinks;

import java.util.UUID;

public record ConnectionContext(
        WebSocketSession session,
        Sinks.Many<String> outbound,
        UUID workspaceId,
        UUID documentId,
        GatewayUser user
) {

    public String sessionId() {
        return session.getId();
    }
}
