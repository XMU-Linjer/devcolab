package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.application.DocumentBlockStructureDto;

import java.util.UUID;

public record DocumentBlockStructureResponse(
        UUID blockId,
        String blockType,
        int sortOrder,
        long version,
        String plainText,
        String content,
        boolean isContentTruncated
) {
    public static DocumentBlockStructureResponse from(DocumentBlockStructureDto dto) {
        return new DocumentBlockStructureResponse(
                dto.blockId(),
                dto.blockType(),
                dto.sortOrder(),
                dto.version(),
                dto.plainText(),
                dto.content(),
                dto.isContentTruncated()
        );
    }
}
