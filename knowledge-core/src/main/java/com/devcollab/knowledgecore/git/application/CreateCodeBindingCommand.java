package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.git.domain.CodeAnchorKind;

import java.util.UUID;

public record CreateCodeBindingCommand(
        UUID repositoryId,
        UUID blockId,
        String pathPattern,
        String revision,
        CodeAnchorKind anchorKind,
        String symbolKey,
        Integer startLine,
        Integer endLine
) {
    public CreateCodeBindingCommand(
            UUID repositoryId,
            UUID blockId,
            String pathPattern
    ) {
        this(
                repositoryId,
                blockId,
                pathPattern,
                null,
                CodeAnchorKind.FILE,
                null,
                null,
                null
        );
    }
}
