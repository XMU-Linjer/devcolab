package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.domain.CodeDocumentBinding;

import java.time.Instant;
import java.util.UUID;

public record CodeDocumentBindingResponse(
        UUID id,
        UUID workspaceId,
        UUID repositoryId,
        UUID documentId,
        UUID blockId,
        String pathPattern,
        Instant createdAt
) {
    public static CodeDocumentBindingResponse from(CodeDocumentBinding binding) {
        return new CodeDocumentBindingResponse(
                binding.id(), binding.workspaceId(), binding.repositoryId(),
                binding.documentId(), binding.blockId(), binding.pathPattern(),
                binding.createdAt()
        );
    }
}
