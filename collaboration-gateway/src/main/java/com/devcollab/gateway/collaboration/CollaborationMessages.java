package com.devcollab.gateway.collaboration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CollaborationMessages {

    private CollaborationMessages() {
    }

    public record ClientMessage(
            String type,
            UUID blockId,
            UUID clientOperationId,
            String operationType,
            Long expectedVersion,
            DocumentOperationContent content
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

        public static ServerMessage roomState(
                List<PresenceMember> members,
                List<EditingState> editingStates
        ) {
            return new ServerMessage(
                    "ROOM_STATE_SNAPSHOT",
                    new RoomStateSnapshot(members, editingStates)
            );
        }

        public static ServerMessage error(String message) {
            return new ServerMessage("ERROR", new ErrorPayload(message));
        }

        public static ServerMessage pong() {
            return new ServerMessage("PONG", new PongPayload(Instant.now()));
        }

        public static ServerMessage operationResult(
                DocumentOperationResult payload
        ) {
            return new ServerMessage("DOCUMENT_OPERATION_RESULT", payload);
        }

        public static ServerMessage operationBroadcast(
                DocumentOperationBroadcast payload
        ) {
            return new ServerMessage("DOCUMENT_OPERATION_BROADCAST", payload);
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

    public record RoomStateSnapshot(
            List<PresenceMember> members,
            List<EditingState> editingStates
    ) {
    }

    public record ErrorPayload(String message) {
    }

    public record PongPayload(Instant at) {
    }

    public record DocumentOperationContent(String text) {
    }

    public record DocumentOperationResult(
            UUID clientOperationId,
            UUID blockId,
            String operationType,
            String status,
            CoreBlockResponse block,
            String message
    ) {
        public static DocumentOperationResult applied(
                UUID clientOperationId,
                UUID blockId,
                String operationType,
                CoreBlockResponse block
        ) {
            return new DocumentOperationResult(
                    clientOperationId,
                    blockId,
                    operationType,
                    "APPLIED",
                    block,
                    null
            );
        }

        public static DocumentOperationResult duplicate(
                UUID clientOperationId,
                UUID blockId,
                String operationType
        ) {
            return new DocumentOperationResult(
                    clientOperationId,
                    blockId,
                    operationType,
                    "DUPLICATE",
                    null,
                    "Operation has already been processed by gateway"
            );
        }

        public static DocumentOperationResult conflict(
                UUID clientOperationId,
                UUID blockId,
                String operationType,
                String message
        ) {
            return new DocumentOperationResult(
                    clientOperationId,
                    blockId,
                    operationType,
                    "CONFLICT",
                    null,
                    message
            );
        }

        public static DocumentOperationResult rejected(
                UUID clientOperationId,
                UUID blockId,
                String operationType,
                String message
        ) {
            return new DocumentOperationResult(
                    clientOperationId,
                    blockId,
                    operationType,
                    "REJECTED",
                    null,
                    message
            );
        }
    }

    public record DocumentOperationBroadcast(
            UUID clientOperationId,
            UUID blockId,
            String operationType,
            UUID userId,
            String username,
            CoreBlockResponse block
    ) {
    }

    public record CoreBlockResponse(
            UUID id,
            UUID documentId,
            String type,
            CoreBlockContent content,
            int sortOrder,
            long version,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CoreBlockContent(String text) {
    }
}
