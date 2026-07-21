package com.devcollab.knowledgecore.git.application;

import java.util.UUID;

public record CreateCodeBindingCommand(
        UUID repositoryId,
        UUID blockId,
        String pathPattern
) {
}
