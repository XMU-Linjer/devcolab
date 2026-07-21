package com.devcollab.knowledgecore.git.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitKnowledgeRepository {

    GitRepository saveRepository(GitRepository repository);

    Optional<GitRepository> findRepositoryById(UUID repositoryId);

    Optional<GitRepository> findRepositoryByRemoteUrl(UUID workspaceId, String remoteUrl);

    List<GitRepository> findRepositoriesByWorkspaceId(UUID workspaceId);

    void markRepositorySyncPending(UUID repositoryId, Instant updatedAt);

    void deleteRepository(UUID repositoryId);

    List<GitRepositoryFile> findFilesByRepositoryId(UUID repositoryId);

    GitChange saveChange(GitChange change);

    Optional<GitChange> findChangeById(UUID changeId);

    Optional<GitChange> findChangeByExternalIdentity(
            UUID repositoryId,
            GitChangeType type,
            String externalId
    );

    List<GitChange> findChangesByRepositoryId(UUID repositoryId);

    void saveDiffs(List<GitFileDiff> diffs);

    List<GitFileDiff> findDiffsByChangeId(UUID changeId);

    CodeDocumentBinding saveBinding(CodeDocumentBinding binding);

    Optional<CodeDocumentBinding> findBindingById(UUID bindingId);

    List<CodeDocumentBinding> findBindingsByDocumentId(UUID documentId);

    List<CodeDocumentBinding> findBindingsByRepositoryId(UUID repositoryId);

    void deleteBinding(UUID bindingId);
}
