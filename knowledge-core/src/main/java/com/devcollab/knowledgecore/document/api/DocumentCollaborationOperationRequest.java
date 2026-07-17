package com.devcollab.knowledgecore.document.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record DocumentCollaborationOperationRequest(
        @NotNull UUID clientOperationId,
        @NotNull UUID blockId,
        @NotBlank String operationType,
        @NotNull @PositiveOrZero Long expectedVersion,
        @Valid @NotNull DocumentBlockContentRequest content
) {
}
