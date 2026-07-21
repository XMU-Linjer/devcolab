package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.git.domain.GitChange;
import com.devcollab.knowledgecore.git.domain.GitFileDiff;

import java.util.List;

public record GitChangeDetails(
        GitChange change,
        List<GitFileDiff> files,
        boolean duplicate
) {
}
