package com.devcollab.knowledgecore.git.domain;

import java.util.UUID;

public record CodeFileDependency(
        UUID id,
        UUID repositoryId,
        String sourcePath,
        String targetPath,
        String relationType
) {
}
