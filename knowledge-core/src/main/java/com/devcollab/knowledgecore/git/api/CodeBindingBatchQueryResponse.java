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
                        file.fileHasBindings(),
                        file.bindings().stream()
                                .map(CodeBindingQueryItemResponse::from)
                                .toList(),
                        file.isTruncated(),
                        file.omittedBindingCount()
                )).toList()
        );
    }

    public record FileBindings(
            String filePath,
            boolean fileHasBindings,
            List<CodeBindingQueryItemResponse> bindings,
            boolean truncated,
            int omittedBindingCount
    ) {
    }
}
