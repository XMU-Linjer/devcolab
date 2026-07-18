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
            String blockType,
            Integer targetIndex,
            Long afterDocumentSequence,
            Integer limit,
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

        public static ServerMessage operationCatchUp(
                DocumentOperationCatchUp payload
        ) {
            return new ServerMessage("DOCUMENT_OPERATION_CATCH_UP", payload);
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
            Long documentSequence,
            CoreBlockResponse block,
            List<CoreBlockResponse> blocks,
            String message
    ) {
        public static DocumentOperationResult applied(
                UUID clientOperationId,
                UUID blockId,
                String operationType,
                long documentSequence,
                CoreBlockResponse block,
                List<CoreBlockResponse> blocks
        ) {
            return new DocumentOperationResult(
                    clientOperationId,
                    blockId,
                    operationType,
                    "APPLIED",
                    documentSequence,
                    block,
                    blocks,
                    null
            );
        }

        public static DocumentOperationResult duplicate(
                UUID clientOperationId,
                UUID blockId,
                String operationType,
                long documentSequence,
                CoreBlockResponse block,
                List<CoreBlockResponse> blocks
        ) {
            return new DocumentOperationResult(
                    clientOperationId,
                    blockId,
                    operationType,
                    "DUPLICATE",
                    documentSequence,
                    block,
                    blocks,
                    "Operation has already been processed"
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
                    null,
                    List.of(),
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
                    null,
                    List.of(),
                    message
            );
        }
    }

    public record DocumentOperationBroadcast(
            UUID clientOperationId,
            UUID blockId,
            String operationType,
            long documentSequence,
            UUID userId,
            String username,
            CoreBlockResponse block,
            List<CoreBlockResponse> blocks
    ) {
    }

    public record DocumentOperationCatchUp(
            long requestedAfterSequence,
            long latestDocumentSequence,
            boolean hasMore,
            List<RecoveredDocumentOperation> operations
    ) {
    }

    public record RecoveredDocumentOperation(
            UUID clientOperationId,
            UUID blockId,
            String operationType,
            long documentSequence,
            UUID operatorUserId,
            CoreBlockResponse block,
            List<CoreBlockResponse> blocks
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
