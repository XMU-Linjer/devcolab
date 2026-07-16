package com.devcollab.gateway.collaboration;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GatewayOperationDeduplicator {

    private final ConcurrentHashMap<OperationKey, Boolean> accepted =
            new ConcurrentHashMap<>();

    public boolean markFirstSeen(
            UUID documentId,
            UUID userId,
            UUID clientOperationId
    ) {
        return accepted.putIfAbsent(
                new OperationKey(documentId, userId, clientOperationId),
                Boolean.TRUE
        ) == null;
    }

    public void forget(
            UUID documentId,
            UUID userId,
            UUID clientOperationId
    ) {
        accepted.remove(new OperationKey(documentId, userId, clientOperationId));
    }

    private record OperationKey(
            UUID documentId,
            UUID userId,
            UUID clientOperationId
    ) {
    }
}
