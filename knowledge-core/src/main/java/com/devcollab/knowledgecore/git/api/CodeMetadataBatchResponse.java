package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.application.CodeMetadataBatchResult;

import java.util.List;
import java.util.UUID;

public record CodeMetadataBatchResponse(
        UUID workspaceId,
        UUID repositoryId,
        String revision,
        List<CodeMetadataBatchResult.FileMetadata> files
) {
    public static CodeMetadataBatchResponse from(CodeMetadataBatchResult result) {
        return new CodeMetadataBatchResponse(
                result.workspaceId(), result.repositoryId(), result.revision(),
                result.files()
        );
    }
}
