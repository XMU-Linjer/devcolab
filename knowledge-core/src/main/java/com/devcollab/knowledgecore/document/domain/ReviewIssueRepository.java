package com.devcollab.knowledgecore.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewIssueRepository {

    ReviewIssue save(ReviewIssue issue);

    Optional<ReviewIssue> findById(UUID issueId);

    List<ReviewIssue> findAllByDocumentVersionId(UUID documentVersionId);
}
