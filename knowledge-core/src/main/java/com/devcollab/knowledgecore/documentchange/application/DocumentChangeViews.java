package com.devcollab.knowledgecore.documentchange.application;

import com.devcollab.knowledgecore.documentchange.domain.DocumentChangeModel.OperationType;
import com.devcollab.knowledgecore.documentchange.domain.DocumentChangeModel.SourceType;
import com.devcollab.knowledgecore.documentchange.domain.DocumentChangeModel.Status;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DocumentChangeViews {

    private DocumentChangeViews() {
    }

    public record UserView(UUID id, String displayName) {
    }

    public record RequestView(
            UUID id,
            UUID workspaceId,
            Status status,
            String summary,
            String rationale,
            SourceType sourceType,
            UserView submittedBy,
            Instant createdAt,
            UserView reviewedBy,
            Instant reviewedAt,
            String rejectionReason
    ) {
    }

    public record TargetView(
            UUID documentId,
            String documentTitle,
            UUID blockId,
            String blockType
    ) {
    }

    public record SnapshotView(
            Long blockVersion,
            String blockType,
            String plainText,
            JsonNode content,
            Integer sortOrder
    ) {
    }

    public record ProposalView(
            String documentTitle,
            String documentType,
            UUID parentDocumentId,
            String blockType,
            String plainText,
            JsonNode content
    ) {
    }

    public record ConflictView(
            boolean conflicted,
            String reason,
            Long expectedVersion,
            Long actualVersion
    ) {
    }

    public record RepositoryView(UUID id, String name) {
    }

    public record EvidenceView(
            UUID id,
            RepositoryView repository,
            String filePath,
            String commitHash,
            Integer startLine,
            Integer endLine,
            String description,
            String excerptText
    ) {
    }

    public record OperationView(
            UUID operationId,
            String clientOperationId,
            int sequenceNumber,
            OperationType operationType,
            TargetView target,
            SnapshotView baseSnapshot,
            ProposalView proposal,
            Long currentBlockVersion,
            ConflictView conflict,
            List<EvidenceView> evidence
    ) {
    }

    public record DetailView(
            RequestView request,
            List<OperationView> operations,
            List<EvidenceView> requestEvidence,
            boolean replayed
    ) {
        public DetailView(
                RequestView request,
                List<OperationView> operations,
                List<EvidenceView> requestEvidence
        ) {
            this(request, operations, requestEvidence, false);
        }
    }

    public record ListItemView(
            UUID id,
            String summary,
            Status status,
            SourceType sourceType,
            String submittedByDisplayName,
            Instant createdAt,
            Instant reviewedAt,
            long operationCount,
            long evidenceCount,
            List<String> affectedDocumentTitles
    ) {
    }

    public record PageView(
            List<ListItemView> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
