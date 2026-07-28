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
            List<Binding> bindings
    ) {
    }

    public record Binding(
            UUID bindingId,
            UUID repositoryId,
            UUID documentId,
            UUID blockId,
            String pathPattern
    ) {
    }
}
