package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.domain.ReviewIssueStatus;

public record UpdateReviewIssueStatusCommand(ReviewIssueStatus status) {
}
