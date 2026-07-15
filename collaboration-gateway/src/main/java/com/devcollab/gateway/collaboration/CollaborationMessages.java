package com.devcollab.gateway.collaboration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CollaborationMessages {

    private CollaborationMessages() {
    }

    public record ClientMessage(
            String type,
            UUID blockId
    ) {
    }

    public record ServerMessage(
            String type,
            Object payload
    ) {

        public static ServerMessage presence(List<PresenceMember> members) {
            return new ServerMessage("PRESENCE_UPDATED", members);
        }

        public static ServerMessage editing(List<EditingState> editingStates) {
            return new ServerMessage("EDITING_UPDATED", editingStates);
        }

        public static ServerMessage error(String message) {
            return new ServerMessage("ERROR", new ErrorPayload(message));
        }

        public static ServerMessage pong() {
            return new ServerMessage("PONG", new PongPayload(Instant.now()));
        }
    }

    public record PresenceMember(
            String sessionId,
            UUID userId,
            String username,
            Instant joinedAt
    ) {
    }

    public record EditingState(
            UUID blockId,
            UUID userId,
            String username,
            Instant startedAt
    ) {
    }

    public record ErrorPayload(String message) {
    }

    public record PongPayload(Instant at) {
    }
}
