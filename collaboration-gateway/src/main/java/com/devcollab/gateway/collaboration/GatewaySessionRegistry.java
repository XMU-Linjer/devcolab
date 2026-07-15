package com.devcollab.gateway.collaboration;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GatewaySessionRegistry {

    private final ConcurrentHashMap<UUID, Set<ConnectionContext>> rooms =
            new ConcurrentHashMap<>();

    public void register(ConnectionContext context) {
        rooms.computeIfAbsent(context.documentId(), ignored ->
                ConcurrentHashMap.newKeySet()
        ).add(context);
    }

    public void unregister(ConnectionContext context) {
        Set<ConnectionContext> connections = rooms.get(context.documentId());
        if (connections == null) {
            return;
        }
        connections.remove(context);
        if (connections.isEmpty()) {
            rooms.remove(context.documentId(), connections);
        }
    }

    public void broadcast(UUID documentId, String message) {
        Set<ConnectionContext> connections = rooms.get(documentId);
        if (connections == null) {
            return;
        }
        for (ConnectionContext connection : connections) {
            connection.outbound().tryEmitNext(message);
        }
    }
}
