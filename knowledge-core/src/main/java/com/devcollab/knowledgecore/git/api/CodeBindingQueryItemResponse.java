package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.application.CodeBindingQueryItem;
import java.util.UUID;

public record CodeBindingQueryItemResponse(
        UUID bindingId,
        UUID documentId,
        UUID blockId,
        String pathPattern,
        String documentTitle
) {
    public static CodeBindingQueryItemResponse from(CodeBindingQueryItem item) {
        return new CodeBindingQueryItemResponse(
                item.bindingId(),
                item.documentId(),
                item.blockId(),
                item.pathPattern(),
                item.documentTitle()
        );
    }
}
