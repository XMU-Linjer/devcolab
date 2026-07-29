package com.devcollab.knowledgecore.git.application;

import java.util.List;
import java.util.UUID;

public record CodeBindingBatchQueryResult(
        UUID workspaceId,
        UUID repositoryId,
        List<FileBindings> files
) {
    public record FileBindings(
            String filePath,
            boolean fileHasBindings,
            List<CodeBindingQueryItem> bindings,
            boolean isTruncated,
            int omittedBindingCount
    ) {
    }
}
