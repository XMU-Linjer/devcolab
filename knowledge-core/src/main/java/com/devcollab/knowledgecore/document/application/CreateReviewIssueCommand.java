package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.domain.ReviewIssueSeverity;
import com.devcollab.knowledgecore.document.domain.ReviewIssueType;

import java.util.UUID;

public record CreateReviewIssueCommand(
        ReviewIssueType type,
        ReviewIssueSeverity severity,
        UUID assigneeId,
        String title,
        String description
) {
}
