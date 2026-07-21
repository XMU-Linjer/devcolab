package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.document.application.exception.DocumentBlockNotFoundException;
import com.devcollab.knowledgecore.common.outbox.application.OutboxEventPublisher;
import com.devcollab.knowledgecore.common.outbox.application.OutboxEventTypes;
import com.devcollab.knowledgecore.document.application.exception.DocumentNotFoundException;
import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.knowledgecore.document.domain.DocumentBlockRepository;
import com.devcollab.knowledgecore.document.domain.DocumentRepository;
import com.devcollab.knowledgecore.git.application.exception.GitChangeNotFoundException;
import com.devcollab.knowledgecore.git.application.exception.GitRepositoryAlreadyExistsException;
import com.devcollab.knowledgecore.git.application.exception.GitRepositoryNotFoundException;
import com.devcollab.knowledgecore.git.application.exception.InvalidCodeBindingException;
import com.devcollab.knowledgecore.git.domain.CodeDocumentBinding;
import com.devcollab.knowledgecore.git.domain.GitChange;
import com.devcollab.knowledgecore.git.domain.GitFileDiff;
import com.devcollab.knowledgecore.git.domain.GitKnowledgeRepository;
import com.devcollab.knowledgecore.git.domain.GitRepository;
import com.devcollab.knowledgecore.git.domain.GitRepositoryFile;
import com.devcollab.knowledgecore.git.domain.GitRepositoryStatus;
import com.devcollab.knowledgecore.workspace.application.WorkspaceApplicationService;
import com.devcollab.knowledgecore.workspace.application.WorkspacePermissionPolicy;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceAccessDeniedException;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class GitKnowledgeApplicationService {

    private final GitKnowledgeRepository gitRepository;
    private final WorkspaceApplicationService workspaceService;
    private final WorkspacePermissionPolicy permissionPolicy;
    private final DocumentRepository documentRepository;
    private final DocumentBlockRepository blockRepository;
    private final OutboxEventPublisher outboxPublisher;

    public GitKnowledgeApplicationService(
            GitKnowledgeRepository gitRepository,
            WorkspaceApplicationService workspaceService,
            WorkspacePermissionPolicy permissionPolicy,
            DocumentRepository documentRepository,
            DocumentBlockRepository blockRepository,
            OutboxEventPublisher outboxPublisher
    ) {
        this.gitRepository = gitRepository;
        this.workspaceService = workspaceService;
        this.permissionPolicy = permissionPolicy;
        this.documentRepository = documentRepository;
        this.blockRepository = blockRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public GitRepository registerRepository(
            UUID workspaceId,
            UUID currentUserId,
            RegisterGitRepositoryCommand command
    ) {
        requireAdmin(workspaceId, currentUserId);
        String remoteUrl = normalizeRemoteUrl(command.remoteUrl());
        gitRepository.findRepositoryByRemoteUrl(workspaceId, remoteUrl)
                .ifPresent(existing -> {
                    throw new GitRepositoryAlreadyExistsException();
                });
        Instant now = Instant.now();
        GitRepositoryStatus status = command.provider().name().equals("GITHUB")
                ? GitRepositoryStatus.SYNC_PENDING
                : GitRepositoryStatus.REGISTERED;
        GitRepository saved = gitRepository.saveRepository(new GitRepository(
                UUID.randomUUID(), workspaceId, command.name().trim(),
                command.provider(), remoteUrl, command.defaultBranch().trim(),
                currentUserId, now, now, status, null, null, null
        ));
        if (status == GitRepositoryStatus.SYNC_PENDING) {
            publishSyncRequest(saved);
        }
        return saved;
    }

    public List<GitRepository> listRepositories(
            UUID workspaceId,
            UUID currentUserId
    ) {
        workspaceService.requireMembership(workspaceId, currentUserId);
        return gitRepository.findRepositoriesByWorkspaceId(workspaceId);
    }

    @Transactional
    public GitRepository requestSync(
            UUID workspaceId,
            UUID repositoryId,
            UUID currentUserId
    ) {
        requireAdmin(workspaceId, currentUserId);
        GitRepository repository = requireRepository(repositoryId, workspaceId);
        if (repository.provider().name().equals("GITHUB")) {
            Instant now = Instant.now();
            gitRepository.markRepositorySyncPending(repositoryId, now);
            publishSyncRequest(repository);
            return new GitRepository(
                    repository.id(), repository.workspaceId(), repository.name(),
                    repository.provider(), repository.remoteUrl(),
                    repository.defaultBranch(), repository.createdBy(),
                    repository.createdAt(), now,
                    GitRepositoryStatus.SYNC_PENDING,
                    repository.lastSyncedCommit(), repository.lastSyncedAt(), null
            );
        }
        throw new InvalidCodeBindingException("当前自动同步仅支持 GitHub 仓库");
    }

    @Transactional
    public void deleteRepository(
            UUID workspaceId,
            UUID repositoryId,
            UUID currentUserId
    ) {
        requireAdmin(workspaceId, currentUserId);
        GitRepository repository = requireRepository(repositoryId, workspaceId);
        outboxPublisher.publish(
                "GIT_REPOSITORY",
                repository.id(),
                OutboxEventTypes.GIT_REPOSITORY_DELETE_REQUESTED,
                Map.of(
                        "workspaceId", workspaceId.toString(),
                        "repositoryId", repositoryId.toString()
                )
        );
        gitRepository.deleteRepository(repositoryId);
    }

    public List<GitRepositoryFile> listFiles(
            UUID workspaceId,
            UUID repositoryId,
            UUID currentUserId
    ) {
        workspaceService.requireMembership(workspaceId, currentUserId);
        requireRepository(repositoryId, workspaceId);
        return gitRepository.findFilesByRepositoryId(repositoryId);
    }

    public CodeGraphDetails getCodeGraph(
            UUID workspaceId,
            UUID repositoryId,
            UUID currentUserId,
            String filePath
    ) {
        workspaceService.requireMembership(workspaceId, currentUserId);
        requireRepository(repositoryId, workspaceId);
        String normalizedPath = filePath == null || filePath.isBlank()
                ? null : normalizePath(filePath);
        return new CodeGraphDetails(
                gitRepository.findSymbolsByRepositoryId(
                        repositoryId, normalizedPath
                ),
                gitRepository.findSymbolDependenciesByRepositoryId(
                        repositoryId, normalizedPath
                ),
                gitRepository.findFileDependenciesByRepositoryId(
                        repositoryId, normalizedPath
                )
        );
    }

    @Transactional
    public GitChangeDetails ingestChange(
            UUID workspaceId,
            UUID repositoryId,
            UUID currentUserId,
            IngestGitChangeCommand command
    ) {
        requireAdmin(workspaceId, currentUserId);
        GitRepository repository = requireRepository(repositoryId, workspaceId);
        var duplicate = gitRepository.findChangeByExternalIdentity(
                repository.id(), command.changeType(), command.externalId().trim()
        );
        if (duplicate.isPresent()) {
            GitChange existing = duplicate.get();
            return new GitChangeDetails(
                    existing,
                    gitRepository.findDiffsByChangeId(existing.id()),
                    true
            );
        }
        if (!command.commitSha().matches("[0-9a-fA-F]{7,64}")) {
            throw new InvalidCodeBindingException("commitSha 格式不合法");
        }
        Instant now = Instant.now();
        GitChange change = gitRepository.saveChange(new GitChange(
                UUID.randomUUID(), repositoryId, command.changeType(),
                command.externalId().trim(), command.title().trim(),
                command.commitSha().toLowerCase(Locale.ROOT), trimToNull(command.baseRef()),
                trimToNull(command.headRef()), trimToNull(command.authorName()),
                trimToNull(command.authorEmail()), command.authoredAt(),
                trimToNull(command.committerName()), trimToNull(command.committerEmail()),
                trimToNull(command.parentCommitSha()),
                trimToNull(command.webUrl()), command.occurredAt(), now
        ));
        List<GitFileDiff> diffs = command.files().stream()
                .map(file -> new GitFileDiff(
                        UUID.randomUUID(), change.id(), normalizePath(file.path()),
                        normalizeNullablePath(file.oldPath()), file.changeType(),
                        file.additions(), file.deletions(), file.binaryFile(),
                        trimToNull(file.patchExcerpt())
                ))
                .toList();
        gitRepository.saveDiffs(diffs);
        outboxPublisher.publish(
                "GIT_CHANGE",
                change.id(),
                OutboxEventTypes.GIT_CHANGE_SYNCED,
                Map.of(
                        "workspaceId", workspaceId.toString(),
                        "repositoryId", repositoryId.toString(),
                        "changeId", change.id().toString(),
                        "changeType", change.changeType().name(),
                        "commitSha", change.commitSha()
                )
        );
        return new GitChangeDetails(change, diffs, false);
    }

    public List<GitChangeDetails> listChanges(
            UUID workspaceId,
            UUID repositoryId,
            UUID currentUserId
    ) {
        workspaceService.requireMembership(workspaceId, currentUserId);
        requireRepository(repositoryId, workspaceId);
        return gitRepository.findChangesByRepositoryId(repositoryId).stream()
                .map(change -> new GitChangeDetails(
                        change,
                        gitRepository.findDiffsByChangeId(change.id()),
                        false
                ))
                .toList();
    }

    @Transactional
    public CodeDocumentBinding createBinding(
            UUID documentId,
            UUID currentUserId,
            CreateCodeBindingCommand command
    ) {
        Document document = requireDocument(documentId);
        workspaceService.requireMembership(document.workspaceId(), currentUserId);
        requireRepository(command.repositoryId(), document.workspaceId());
        if (command.blockId() != null) {
            DocumentBlock block = blockRepository.findById(command.blockId())
                    .orElseThrow(DocumentBlockNotFoundException::new);
            if (!block.documentId().equals(documentId)) {
                throw new InvalidCodeBindingException("Block 不属于目标文档");
            }
        }
        String pattern = normalizePattern(command.pathPattern());
        boolean duplicate = gitRepository.findBindingsByDocumentId(documentId)
                .stream()
                .anyMatch(binding -> binding.repositoryId().equals(command.repositoryId())
                        && java.util.Objects.equals(binding.blockId(), command.blockId())
                        && binding.pathPattern().equals(pattern));
        if (duplicate) {
            throw new InvalidCodeBindingException("该代码路径关联已存在");
        }
        return gitRepository.saveBinding(new CodeDocumentBinding(
                UUID.randomUUID(), document.workspaceId(), command.repositoryId(),
                documentId, command.blockId(), pattern, currentUserId, Instant.now()
        ));
    }

    public List<CodeDocumentBinding> listBindings(
            UUID documentId,
            UUID currentUserId
    ) {
        Document document = requireDocument(documentId);
        workspaceService.requireMembership(document.workspaceId(), currentUserId);
        return gitRepository.findBindingsByDocumentId(documentId);
    }

    @Transactional
    public void deleteBinding(UUID bindingId, UUID currentUserId) {
        CodeDocumentBinding binding = gitRepository.findBindingById(bindingId)
                .orElseThrow(() -> new InvalidCodeBindingException("代码路径关联不存在"));
        workspaceService.requireMembership(binding.workspaceId(), currentUserId);
        gitRepository.deleteBinding(bindingId);
    }

    public List<AffectedCodeDocument> findAffectedDocuments(
            UUID workspaceId,
            UUID changeId,
            UUID currentUserId
    ) {
        workspaceService.requireMembership(workspaceId, currentUserId);
        GitChange change = gitRepository.findChangeById(changeId)
                .orElseThrow(GitChangeNotFoundException::new);
        requireRepository(change.repositoryId(), workspaceId);
        List<String> changedPaths = gitRepository.findDiffsByChangeId(changeId)
                .stream().flatMap(diff -> {
                    LinkedHashSet<String> paths = new LinkedHashSet<>();
                    paths.add(diff.path());
                    if (diff.oldPath() != null) {
                        paths.add(diff.oldPath());
                    }
                    return paths.stream();
                }).toList();
        List<AffectedCodeDocument> result = new ArrayList<>();
        for (CodeDocumentBinding binding
                : gitRepository.findBindingsByRepositoryId(change.repositoryId())) {
            List<String> matches = changedPaths.stream()
                    .filter(path -> matches(binding.pathPattern(), path))
                    .toList();
            if (!matches.isEmpty()) {
                result.add(new AffectedCodeDocument(
                        binding.id(), binding.documentId(), binding.blockId(),
                        binding.pathPattern(), matches
                ));
            }
        }
        return result;
    }

    private GitRepository requireRepository(UUID repositoryId, UUID workspaceId) {
        GitRepository repository = gitRepository.findRepositoryById(repositoryId)
                .orElseThrow(GitRepositoryNotFoundException::new);
        if (!repository.workspaceId().equals(workspaceId)) {
            throw new GitRepositoryNotFoundException();
        }
        return repository;
    }

    private Document requireDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(DocumentNotFoundException::new);
    }

    private void requireAdmin(UUID workspaceId, UUID userId) {
        WorkspaceMember member = workspaceService.requireMembership(workspaceId, userId);
        if (!permissionPolicy.isAdmin(member)) {
            throw new WorkspaceAccessDeniedException();
        }
    }

    private String normalizeRemoteUrl(String value) {
        String trimmed = value.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException exception) {
            throw new InvalidCodeBindingException("remoteUrl 格式不合法");
        }
        if (uri.getScheme() == null || uri.getHost() == null
                || !(uri.getScheme().equals("https") || uri.getScheme().equals("http"))) {
            throw new InvalidCodeBindingException("remoteUrl 必须是 HTTP(S) 地址");
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String normalizePattern(String value) {
        String pattern = normalizePath(value);
        if (pattern.contains("*") && !(pattern.endsWith("/**") || pattern.startsWith("**/*."))) {
            throw new InvalidCodeBindingException("仅支持精确路径、目录/** 或 **/*.扩展名");
        }
        return pattern;
    }

    private String normalizeNullablePath(String value) {
        return value == null || value.isBlank() ? null : normalizePath(value);
    }

    private String normalizePath(String value) {
        String path = value.trim().replace('\\', '/');
        if (path.isBlank() || path.startsWith("/") || path.contains("../")
                || path.equals("..") || path.contains("//")) {
            throw new InvalidCodeBindingException("代码路径必须是仓库内相对路径");
        }
        return path;
    }

    private boolean matches(String pattern, String path) {
        if (pattern.endsWith("/**")) {
            return path.startsWith(pattern.substring(0, pattern.length() - 2));
        }
        if (pattern.startsWith("**/*.")) {
            return path.endsWith(pattern.substring(4));
        }
        return pattern.equals(path);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void publishSyncRequest(GitRepository repository) {
        outboxPublisher.publish(
                "GIT_REPOSITORY",
                repository.id(),
                OutboxEventTypes.GIT_REPOSITORY_SYNC_REQUESTED,
                Map.of(
                        "workspaceId", repository.workspaceId().toString(),
                        "repositoryId", repository.id().toString(),
                        "remoteUrl", repository.remoteUrl(),
                        "defaultBranch", repository.defaultBranch()
                )
        );
    }
}
