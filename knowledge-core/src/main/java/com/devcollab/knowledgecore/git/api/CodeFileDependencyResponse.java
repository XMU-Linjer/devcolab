package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.domain.CodeFileDependency;

public record CodeFileDependencyResponse(
        String sourcePath,
        String targetPath,
        String relationType
) {
    public static CodeFileDependencyResponse from(CodeFileDependency dependency) {
        return new CodeFileDependencyResponse(
                dependency.sourcePath(), dependency.targetPath(),
                dependency.relationType()
        );
    }
}
