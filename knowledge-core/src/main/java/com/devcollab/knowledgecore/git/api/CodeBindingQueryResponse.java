package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.application.CodeBindingQueryResult;
import java.util.List;
import java.util.UUID;

public record CodeBindingQueryResponse(
        UUID workspaceId,
        UUID repositoryId,
        String filePath,
        boolean fileHasBindings,
        List<CodeBindingQueryItemResponse> bindings,
        boolean truncated,
        int omittedBindingCount
) {
    public static CodeBindingQueryResponse from(CodeBindingQueryResult result) {
        return new CodeBindingQueryResponse(
                result.workspaceId(),
                result.repositoryId(),
                result.filePath(),
                result.fileHasBindings(),
                result.bindings().stream().map(CodeBindingQueryItemResponse::from).toList(),
                result.isTruncated(),
                result.omittedBindingCount()
        );
    }
}
