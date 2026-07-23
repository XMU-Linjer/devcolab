package com.devcollab.knowledgecore.git.application;

import java.util.List;
import java.util.UUID;

public record CodeBindingQueryResult(
        UUID workspaceId,
        UUID repositoryId,
        String filePath,
        boolean fileHasBindings,
        List<CodeBindingQueryItem> bindings,
        boolean isTruncated,
        int omittedBindingCount
) {
}
