package com.devcollab.knowledgecore.documentchange.domain;

import java.time.Instant;
import java.util.UUID;

public final class DocumentChangeModel {

    private DocumentChangeModel() {
    }

    public enum Status {
        PENDING, APPLIED, REJECTED, STALE
    }

    public enum SourceType {
        MCP
    }

    public enum OperationType {
        CREATE_DOCUMENT, ADD_BLOCK, UPDATE_BLOCK, DELETE_BLOCK
    }

    public record ChangeRequest(
            UUID id,
            UUID workspaceId,
            String clientRequestId,
            String requestFingerprint,
            Status status,
            String summary,
            String rationale,
            SourceType sourceType,
            UUID submittedBy,
            Instant createdAt,
            UUID reviewedBy,
            Instant reviewedAt,
            String rejectionReason
    ) {
    }

    public record Operation(
            UUID id,
            UUID changeRequestId,
            String clientOperationId,
            int sequenceNumber,
            OperationType operationType,
            UUID documentId,
            UUID createdDocumentOperationId,
            UUID blockId,
            Long baseBlockVersion,
            String originalBlockType,
            String originalPlainText,
            Integer originalContentSchemaVersion,
            String originalContentJson,
            Integer originalSortOrder,
            String proposedDocumentTitle,
            String proposedDocumentType,
            UUID proposedParentDocumentId,
            String proposedBlockType,
            String proposedPlainText,
            Integer proposedContentSchemaVersion,
            String proposedContentJson
    ) {
    }

    public record Evidence(
            UUID id,
            UUID changeRequestId,
            UUID operationId,
            UUID repositoryId,
            String commitHash,
            String filePath,
            Integer startLine,
            Integer endLine,
            String description,
            String blobSha,
            String excerptText,
            String excerptHash
    ) {
    }

    public record ListItem(
            UUID id,
            String summary,
            Status status,
            SourceType sourceType,
            UUID submittedBy,
            String submittedByDisplayName,
            Instant createdAt,
            Instant reviewedAt,
            long operationCount,
            long evidenceCount
    ) {
    }
}
