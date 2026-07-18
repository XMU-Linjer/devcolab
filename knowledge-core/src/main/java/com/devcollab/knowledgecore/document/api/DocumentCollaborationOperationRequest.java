package com.devcollab.knowledgecore.document.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import com.devcollab.knowledgecore.document.domain.DocumentBlockType;

import java.util.UUID;

public record DocumentCollaborationOperationRequest(
        @NotNull UUID clientOperationId,
        UUID blockId,
        @NotBlank String operationType,
        @PositiveOrZero Long expectedVersion,
        DocumentBlockType blockType,
        @PositiveOrZero Integer targetIndex,
        @Valid DocumentBlockContentRequest content
) {
}
