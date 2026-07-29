package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.git.domain.CodeAnchorKind;

import java.util.UUID;

public record CodeBindingQueryItem(
        UUID bindingId,
        UUID workspaceId,
        UUID repositoryId,
        String revision,
        CodeAnchorKind anchorKind,
        String symbolKey,
        Integer startLine,
        Integer endLine,
        UUID documentId,
        UUID blockId,
        String targetKey,
        String pathPattern,
        String documentTitle
) {
}
