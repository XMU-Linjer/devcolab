package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.application.CodeBindingQueryItem;
import com.devcollab.knowledgecore.git.domain.CodeAnchorKind;
import java.util.UUID;

public record CodeBindingQueryItemResponse(
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
    public static CodeBindingQueryItemResponse from(CodeBindingQueryItem item) {
        return new CodeBindingQueryItemResponse(
                item.bindingId(),
                item.workspaceId(),
                item.repositoryId(),
                item.revision(),
                item.anchorKind(),
                item.symbolKey(),
                item.startLine(),
                item.endLine(),
                item.documentId(),
                item.blockId(),
                item.targetKey(),
                item.pathPattern(),
                item.documentTitle()
        );
    }
}
