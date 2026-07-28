package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.application.CodeBindingBatchQueryResult;

import java.util.List;
import java.util.UUID;

public record CodeBindingBatchQueryResponse(
        UUID workspaceId,
        UUID repositoryId,
        List<FileBindings> files
) {
    public static CodeBindingBatchQueryResponse from(CodeBindingBatchQueryResult result) {
        return new CodeBindingBatchQueryResponse(
                result.workspaceId(), result.repositoryId(),
                result.files().stream().map(file -> new FileBindings(
                        file.filePath(),
                        file.bindings().stream().map(binding -> new Binding(
                                binding.bindingId(), binding.repositoryId(),
                                binding.documentId(), binding.documentTitle(),
                                binding.blockId(),
                                binding.pathPattern()
                        )).toList()
                )).toList()
        );
    }

    public record FileBindings(String filePath, List<Binding> bindings) {
    }

    public record Binding(
            UUID bindingId,
            UUID repositoryId,
            UUID documentId,
            String documentTitle,
            UUID blockId,
            String pathPattern
    ) {
    }
}
