package com.devcollab.knowledgecore.documentchange.application;

import com.devcollab.knowledgecore.auth.domain.UserAccount;
import com.devcollab.knowledgecore.auth.domain.UserRepository;
import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.knowledgecore.document.domain.DocumentBlockRepository;
import com.devcollab.knowledgecore.document.domain.DocumentRepository;
import com.devcollab.knowledgecore.documentchange.domain.DocumentChangeModel;
import com.devcollab.knowledgecore.documentchange.domain.DocumentChangeRepository;
import com.devcollab.knowledgecore.git.domain.GitKnowledgeRepository;
import com.devcollab.knowledgecore.git.domain.GitRepository;
import com.devcollab.knowledgecore.workspace.application.WorkspaceApplicationService;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceAccessDeniedException;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceNotFoundException;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.devcollab.knowledgecore.documentchange.application.DocumentChangeViews.*;
import static com.devcollab.knowledgecore.documentchange.domain.DocumentChangeModel.*;

@Service
public class DocumentChangeApplicationService {

    private final DocumentChangeRepository repository;
    private final WorkspaceApplicationService workspaceService;
    private final DocumentRepository documentRepository;
    private final DocumentBlockRepository blockRepository;
    private final GitKnowledgeRepository gitRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public DocumentChangeApplicationService(
            DocumentChangeRepository repository,
            WorkspaceApplicationService workspaceService,
            DocumentRepository documentRepository,
            DocumentBlockRepository blockRepository,
            GitKnowledgeRepository gitRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.workspaceService = workspaceService;
        this.documentRepository = documentRepository;
        this.blockRepository = blockRepository;
        this.gitRepository = gitRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public long pendingCount(UUID workspaceId, UUID currentUserId) {
        requireAdmin(workspaceId, currentUserId);
        return repository.count(workspaceId, Status.PENDING);
    }

    public PageView list(
            UUID workspaceId,
            UUID currentUserId,
            Status status,
            int page,
            int size,
            String sort
    ) {
        requireAdmin(workspaceId, currentUserId);
        if (page < 0 || size < 1 || size > 100) {
            throw invalid("分页参数不合法");
        }
        boolean ascending = switch (sort == null ? "createdAt,desc" : sort) {
            case "createdAt,asc" -> true;
            case "createdAt,desc" -> false;
            default -> throw invalid("仅支持 createdAt,asc 或 createdAt,desc");
        };
        Status resolvedStatus = status == null ? Status.PENDING : status;
        long total = repository.count(workspaceId, resolvedStatus);
        List<ListItemView> items = repository.findPage(
                        workspaceId,
                        resolvedStatus,
                        page * size,
                        size,
                        ascending
                ).stream()
                .map(item -> new ListItemView(
                        item.id(), item.summary(), item.status(),
                        item.sourceType(), item.submittedByDisplayName(),
                        item.createdAt(), item.reviewedAt(),
                        item.operationCount(), item.evidenceCount(),
                        affectedDocumentTitles(item.id())
                ))
                .toList();
        return new PageView(
                items,
                page,
                size,
                total,
                total == 0 ? 0 : (int) ((total + size - 1) / size)
        );
    }

    public DetailView detail(
            UUID workspaceId,
            UUID requestId,
            UUID currentUserId
    ) {
        requireAdmin(workspaceId, currentUserId);
        ChangeRequest request = repository.findRequest(workspaceId, requestId)
                .orElseThrow(() -> notFound("文档变更请求不存在"));
        return detail(request);
    }

    @Transactional(readOnly = true)
    public DetailView detail(ChangeRequest request) {
        List<Operation> operations = repository.findOperations(request.id());
        List<Evidence> evidence = repository.findEvidence(request.id());
        Map<UUID, List<Evidence>> byOperation = evidence.stream()
                .filter(item -> item.operationId() != null)
                .collect(Collectors.groupingBy(Evidence::operationId));
        List<Evidence> requestEvidence = evidence.stream()
                .filter(item -> item.operationId() == null)
                .toList();
        Map<UUID, GitRepository> repositories = evidence.stream()
                .map(Evidence::repositoryId)
                .distinct()
                .map(gitRepository::findRepositoryById)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toMap(GitRepository::id, Function.identity()));

        List<OperationView> operationViews = operations.stream()
                .map(operation -> operationView(
                        operation,
                        byOperation.getOrDefault(operation.id(), List.of()),
                        repositories
                ))
                .toList();
        return new DetailView(
                requestView(request),
                operationViews,
                requestEvidence.stream()
                        .map(item -> evidenceView(item, repositories))
                        .toList()
        );
    }

    private OperationView operationView(
            Operation operation,
            List<Evidence> evidence,
            Map<UUID, GitRepository> repositories
    ) {
        Document document = operation.documentId() == null
                ? null
                : documentRepository.findById(operation.documentId()).orElse(null);
        DocumentBlock block = operation.blockId() == null
                ? null
                : blockRepository.findById(operation.blockId())
                .filter(item -> operation.documentId() == null
                        || item.documentId().equals(operation.documentId()))
                .orElse(null);
        String documentTitle = document == null
                ? operation.proposedDocumentTitle()
                : document.title();
        Long actualVersion = block == null ? null : block.version();
        boolean requiresVersion = operation.operationType() == OperationType.UPDATE_BLOCK
                || operation.operationType() == OperationType.DELETE_BLOCK;
        boolean conflicted = requiresVersion
                && (!Objects.equals(operation.baseBlockVersion(), actualVersion));
        String conflictReason = !conflicted
                ? null
                : block == null ? "BLOCK_NOT_FOUND" : "BLOCK_VERSION_CHANGED";
        return new OperationView(
                operation.id(),
                operation.clientOperationId(),
                operation.sequenceNumber(),
                operation.operationType(),
                new TargetView(
                        operation.documentId(),
                        documentTitle,
                        operation.blockId(),
                        block == null ? operation.originalBlockType()
                                : block.type().name()
                ),
                operation.originalBlockType() == null
                        ? null
                        : new SnapshotView(
                                operation.baseBlockVersion(),
                                operation.originalBlockType(),
                                operation.originalPlainText(),
                                readJson(operation.originalContentJson()),
                                operation.originalSortOrder()
                        ),
                new ProposalView(
                        operation.proposedDocumentTitle(),
                        operation.proposedDocumentType(),
                        operation.proposedParentDocumentId(),
                        operation.proposedBlockType(),
                        operation.proposedPlainText(),
                        readJson(operation.proposedContentJson())
                ),
                actualVersion,
                new ConflictView(
                        conflicted,
                        conflictReason,
                        operation.baseBlockVersion(),
                        actualVersion
                ),
                evidence.stream()
                        .map(item -> evidenceView(item, repositories))
                        .toList()
        );
    }

    private List<String> affectedDocumentTitles(UUID requestId) {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        for (Operation operation : repository.findOperations(requestId)) {
            if (operation.documentId() != null) {
                documentRepository.findById(operation.documentId())
                        .map(Document::title)
                        .ifPresent(titles::add);
            } else if (operation.proposedDocumentTitle() != null) {
                titles.add(operation.proposedDocumentTitle());
            }
        }
        return new ArrayList<>(titles);
    }

    private RequestView requestView(ChangeRequest request) {
        return new RequestView(
                request.id(),
                request.workspaceId(),
                request.status(),
                request.summary(),
                request.rationale(),
                request.sourceType(),
                userView(request.submittedBy()),
                request.createdAt(),
                userView(request.reviewedBy()),
                request.reviewedAt(),
                request.rejectionReason()
        );
    }

    private EvidenceView evidenceView(
            Evidence evidence,
            Map<UUID, GitRepository> repositories
    ) {
        GitRepository git = repositories.get(evidence.repositoryId());
        return new EvidenceView(
                evidence.id(),
                new RepositoryView(
                        evidence.repositoryId(),
                        git == null ? "Unavailable repository" : git.name()
                ),
                evidence.filePath(),
                evidence.commitHash(),
                evidence.startLine(),
                evidence.endLine(),
                evidence.description(),
                evidence.excerptText()
        );
    }

    private UserView userView(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(user -> new UserView(user.id(), user.displayName()))
                .orElse(new UserView(userId, "Unknown user"));
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "存储的文档变更内容不是合法 JSON",
                    exception
            );
        }
    }

    private void requireAdmin(UUID workspaceId, UUID currentUserId) {
        com.devcollab.knowledgecore.workspace.application.WorkspaceView workspace;
        try {
            workspace = workspaceService.get(workspaceId, currentUserId);
        } catch (WorkspaceAccessDeniedException exception) {
            throw new WorkspaceNotFoundException();
        }
        if (workspace.currentUserRole() != WorkspaceRole.ADMIN) {
            throw new WorkspaceAccessDeniedException();
        }
    }

    protected void requireMember(UUID workspaceId, UUID currentUserId) {
        workspaceService.requireMembership(workspaceId, currentUserId);
    }

    protected DocumentChangeException invalid(String message) {
        return new DocumentChangeException(
                HttpStatus.BAD_REQUEST,
                "DOCUMENT_CHANGE_REQUEST_INVALID",
                message
        );
    }

    protected DocumentChangeException notFound(String message) {
        return new DocumentChangeException(
                HttpStatus.NOT_FOUND,
                "DOCUMENT_CHANGE_REQUEST_NOT_FOUND",
                message
        );
    }
}
