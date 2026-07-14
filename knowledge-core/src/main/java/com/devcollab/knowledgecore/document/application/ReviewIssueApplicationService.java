package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.application.exception.ReviewIssueNotFoundException;
import com.devcollab.knowledgecore.document.domain.DocumentVersion;
import com.devcollab.knowledgecore.document.domain.ReviewIssue;
import com.devcollab.knowledgecore.document.domain.ReviewIssueRepository;
import com.devcollab.knowledgecore.document.domain.ReviewIssueStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewIssueApplicationService {

    private final ReviewIssueRepository issueRepository;
    private final DocumentApplicationService documentService;

    public ReviewIssueApplicationService(
            ReviewIssueRepository issueRepository,
            DocumentApplicationService documentService
    ) {
        this.issueRepository = issueRepository;
        this.documentService = documentService;
    }

    @Transactional
    public ReviewIssue create(
            UUID documentId,
            UUID versionId,
            UUID currentUserId,
            CreateReviewIssueCommand command
    ) {
        DocumentVersion version = documentService.getVersion(
                documentId,
                versionId,
                currentUserId
        );
        Instant now = Instant.now();
        ReviewIssue issue = new ReviewIssue(
                UUID.randomUUID(),
                version.id(),
                command.type(),
                command.severity(),
                ReviewIssueStatus.OPEN,
                command.assigneeId(),
                command.title().trim(),
                normalizeDescription(command.description()),
                currentUserId,
                now
        );
        return issueRepository.save(issue);
    }

    public List<ReviewIssue> list(
            UUID documentId,
            UUID versionId,
            UUID currentUserId
    ) {
        DocumentVersion version = documentService.getVersion(
                documentId,
                versionId,
                currentUserId
        );
        return issueRepository.findAllByDocumentVersionId(version.id());
    }

    @Transactional
    public ReviewIssue updateStatus(
            UUID documentId,
            UUID issueId,
            UUID currentUserId,
            UpdateReviewIssueStatusCommand command
    ) {
        documentService.get(documentId, currentUserId);
        if (command.status() == ReviewIssueStatus.OPEN) {
            throw new IllegalArgumentException(
                    "评审问题处理状态不能回退为 OPEN"
            );
        }

        ReviewIssue issue = issueRepository.findById(issueId)
                .orElseThrow(ReviewIssueNotFoundException::new);
        DocumentVersion version = documentService.getVersion(
                documentId,
                issue.documentVersionId(),
                currentUserId
        );

        ReviewIssue updated = new ReviewIssue(
                issue.id(),
                version.id(),
                issue.type(),
                issue.severity(),
                command.status(),
                issue.assigneeId(),
                issue.title(),
                issue.description(),
                issue.createdBy(),
                issue.createdAt()
        );
        return issueRepository.save(updated);
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
