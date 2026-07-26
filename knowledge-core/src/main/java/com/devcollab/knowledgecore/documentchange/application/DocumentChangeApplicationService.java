package com.devcollab.knowledgecore.documentchange.application;

import com.devcollab.knowledgecore.auth.domain.UserAccount;
import com.devcollab.knowledgecore.auth.domain.UserRepository;
import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.knowledgecore.document.domain.DocumentBlockType;
import com.devcollab.knowledgecore.document.domain.DocumentBlockRepository;
import com.devcollab.knowledgecore.document.domain.DocumentRepository;
import com.devcollab.knowledgecore.document.domain.DocumentReviewStatus;
import com.devcollab.knowledgecore.document.domain.DocumentType;
import com.devcollab.knowledgecore.document.application.CreateDocumentBlockCommand;
import com.devcollab.knowledgecore.document.application.CreateDocumentCommand;
import com.devcollab.knowledgecore.document.application.DocumentApplicationService;
import com.devcollab.knowledgecore.document.application.DocumentBlockApplicationService;
import com.devcollab.knowledgecore.document.application.DocumentBlockContentCodec;
import com.devcollab.knowledgecore.document.application.UpdateDocumentBlockCommand;
import com.devcollab.knowledgecore.document.domain.DocumentOperationLog;
import com.devcollab.knowledgecore.document.domain.DocumentOperationLogRepository;
import com.devcollab.knowledgecore.common.outbox.application.OutboxEventPublisher;
import com.devcollab.knowledgecore.common.util.RepositoryPathValidator;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
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
    private final DocumentBlockContentCodec contentCodec;
    private final DocumentApplicationService documentService;
    private final DocumentBlockApplicationService blockService;
    private final DocumentOperationLogRepository operationLogRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    public DocumentChangeApplicationService(
            DocumentChangeRepository repository,
            WorkspaceApplicationService workspaceService,
            DocumentRepository documentRepository,
            DocumentBlockRepository blockRepository,
            GitKnowledgeRepository gitRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            DocumentBlockContentCodec contentCodec,
            DocumentApplicationService documentService,
            DocumentBlockApplicationService blockService,
            DocumentOperationLogRepository operationLogRepository,
            OutboxEventPublisher outboxEventPublisher
    ) {
        this.repository = repository;
        this.workspaceService = workspaceService;
        this.documentRepository = documentRepository;
        this.blockRepository = blockRepository;
        this.gitRepository = gitRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.contentCodec = contentCodec;
        this.documentService = documentService;
        this.blockService = blockService;
        this.operationLogRepository = operationLogRepository;
        this.outboxEventPublisher = outboxEventPublisher;
    }

    public record CreateCommand(
            String clientRequestId,
            String summary,
            String rationale,
            List<CreateOperationCommand> operations,
            List<CreateEvidenceCommand> evidence
    ) {
    }

    public record CreateOperationCommand(
            String clientOperationId,
            int sequenceNumber,
            OperationType operationType,
            UUID documentId,
            String createdDocumentClientOperationId,
            UUID blockId,
            Long baseBlockVersion,
            String proposedDocumentTitle,
            DocumentType proposedDocumentType,
            UUID proposedParentDocumentId,
            DocumentBlockType proposedBlockType,
            String proposedPlainText,
            Integer proposedContentSchemaVersion,
            JsonNode proposedContent
    ) {
    }

    public record CreateEvidenceCommand(
            String clientOperationId,
            UUID repositoryId,
            String filePath,
            Integer startLine,
            Integer endLine,
            String description
    ) {
    }

    public record CreateResult(
            UUID changeRequestId,
            Status status,
            Instant createdAt,
            boolean idempotentReplay
    ) {
    }

    public record DecisionResult(DetailView detail, boolean stale) {
    }

    @Transactional
    public CreateResult create(
            UUID workspaceId,
            UUID currentUserId,
            CreateCommand command
    ) {
        requireMember(workspaceId, currentUserId);
        validateCreateCommand(command);
        String fingerprint = fingerprint(command);
        var existing = repository.findByClientRequestId(
                workspaceId,
                currentUserId,
                command.clientRequestId().trim()
        );
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }

        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();
        ChangeRequest request = new ChangeRequest(
                requestId,
                workspaceId,
                command.clientRequestId().trim(),
                fingerprint,
                Status.PENDING,
                command.summary().trim(),
                command.rationale().trim(),
                SourceType.MCP,
                currentUserId,
                now,
                null,
                null,
                null
        );
        try {
            repository.saveRequest(request);
        } catch (DataIntegrityViolationException exception) {
            ChangeRequest concurrent = repository.findByClientRequestId(
                            workspaceId,
                            currentUserId,
                            request.clientRequestId()
                    )
                    .orElseThrow(() -> exception);
            return replay(concurrent, fingerprint);
        }

        Map<String, Operation> savedByClientId =
                new java.util.LinkedHashMap<>();
        for (CreateOperationCommand input : command.operations().stream()
                .sorted(java.util.Comparator.comparingInt(
                        CreateOperationCommand::sequenceNumber
                ))
                .toList()) {
            Operation operation = buildOperation(
                    workspaceId,
                    requestId,
                    input,
                    savedByClientId
            );
            repository.saveOperation(operation);
            savedByClientId.put(operation.clientOperationId(), operation);
        }
        for (CreateEvidenceCommand input : safeList(command.evidence())) {
            repository.saveEvidence(buildEvidence(
                    workspaceId,
                    requestId,
                    input,
                    savedByClientId
            ));
        }
        return new CreateResult(requestId, Status.PENDING, now, false);
    }

    @Transactional
    public DecisionResult apply(
            UUID workspaceId,
            UUID requestId,
            UUID currentUserId
    ) {
        requireAdmin(workspaceId, currentUserId);
        ChangeRequest request = lockedRequest(workspaceId, requestId);
        if (request.status() == Status.APPLIED) {
            return new DecisionResult(replayedDetail(request), false);
        }
        if (request.status() == Status.STALE) {
            return new DecisionResult(replayedDetail(request), true);
        }
        if (request.status() == Status.REJECTED) {
            throw conflict(
                    "REQUEST_REJECTED",
                    "已拒绝的变更请求不能再次应用"
            );
        }

        List<Operation> operations = repository.findOperations(request.id());
        LockedTargets targets = lockAndValidateTargets(
                workspaceId,
                operations
        );
        if (targets.stale()) {
            Instant reviewedAt = Instant.now();
            ChangeRequest stale = repository.decide(
                    request,
                    Status.STALE,
                    currentUserId,
                    reviewedAt,
                    null
            );
            recordDecision(
                    stale,
                    currentUserId,
                    "DOCUMENT_CHANGE_STALE",
                    "文档变更请求因目标已变化而失效",
                    reviewedAt
            );
            return new DecisionResult(detail(stale), true);
        }

        Map<UUID, UUID> createdDocuments = new java.util.HashMap<>();
        for (Operation operation : operations) {
            applyOperation(
                    workspaceId,
                    currentUserId,
                    operation,
                    createdDocuments,
                    targets.blocks()
            );
        }

        Instant reviewedAt = Instant.now();
        ChangeRequest applied = repository.decide(
                request,
                Status.APPLIED,
                currentUserId,
                reviewedAt,
                null
        );
        recordDecision(
                applied,
                currentUserId,
                "DOCUMENT_CHANGE_APPLIED",
                "批准并原子应用文档变更请求",
                reviewedAt
        );
        return new DecisionResult(detail(applied), false);
    }

    @Transactional
    public DetailView reject(
            UUID workspaceId,
            UUID requestId,
            UUID currentUserId,
            String reason
    ) {
        requireAdmin(workspaceId, currentUserId);
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty() || normalizedReason.length() > 2_000) {
            throw invalid("拒绝理由必须为 1 到 2000 个字符");
        }

        ChangeRequest request = lockedRequest(workspaceId, requestId);
        if (request.status() == Status.REJECTED) {
            if (Objects.equals(request.rejectionReason(), normalizedReason)) {
                return replayedDetail(request);
            }
            throw conflict(
                    "REJECTION_REASON_CONFLICT",
                    "该请求已使用不同理由拒绝"
            );
        }
        if (request.status() == Status.APPLIED) {
            throw conflict(
                    "REQUEST_ALREADY_APPLIED",
                    "已应用的变更请求不能拒绝"
            );
        }
        if (request.status() == Status.STALE) {
            throw conflict(
                    "REQUEST_STALE",
                    "已失效的变更请求不能拒绝"
            );
        }

        Instant reviewedAt = Instant.now();
        ChangeRequest rejected = repository.decide(
                request,
                Status.REJECTED,
                currentUserId,
                reviewedAt,
                normalizedReason
        );
        recordDecision(
                rejected,
                currentUserId,
                "DOCUMENT_CHANGE_REJECTED",
                "拒绝文档变更请求：" + normalizedReason,
                reviewedAt
        );
        return detail(rejected);
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
                        repositories,
                        request.status()
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
            Map<UUID, GitRepository> repositories,
            Status requestStatus
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
        boolean conflictRelevant = requestStatus == Status.PENDING
                || requestStatus == Status.STALE;
        boolean missingDocument = operation.documentId() != null
                && document == null;
        boolean parentMissing = operation.operationType()
                == OperationType.CREATE_DOCUMENT
                && operation.proposedParentDocumentId() != null
                && documentRepository.findById(
                        operation.proposedParentDocumentId()
                ).isEmpty();
        boolean documentNotEditable = document != null
                && document.reviewStatus() != DocumentReviewStatus.DRAFT;
        boolean versionConflict = requiresVersion
                && !Objects.equals(operation.baseBlockVersion(), actualVersion);
        boolean conflicted = conflictRelevant && (
                missingDocument
                        || parentMissing
                        || documentNotEditable
                        || versionConflict
        );
        String conflictReason = !conflicted
                ? null
                : missingDocument ? "DOCUMENT_NOT_FOUND"
                : parentMissing ? "PARENT_DOCUMENT_NOT_FOUND"
                : documentNotEditable ? "DOCUMENT_NOT_EDITABLE"
                : block == null ? "BLOCK_NOT_FOUND"
                : "BLOCK_VERSION_CHANGED";
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

    private ChangeRequest lockedRequest(UUID workspaceId, UUID requestId) {
        return repository.findRequestForUpdate(workspaceId, requestId)
                .orElseThrow(() -> notFound(
                        "文档变更请求不存在"
                ));
    }

    private record LockedTargets(
            Map<UUID, Document> documents,
            Map<UUID, DocumentBlock> blocks,
            boolean stale
    ) {
    }

    private LockedTargets lockAndValidateTargets(
            UUID workspaceId,
            List<Operation> operations
    ) {
        LinkedHashSet<UUID> documentIds = new LinkedHashSet<>();
        for (Operation operation : operations) {
            if (operation.documentId() != null) {
                documentIds.add(operation.documentId());
            }
            if (operation.proposedParentDocumentId() != null) {
                documentIds.add(operation.proposedParentDocumentId());
            }
        }

        Map<UUID, Document> documents = new java.util.LinkedHashMap<>();
        boolean stale = false;
        for (UUID documentId : documentIds.stream().sorted().toList()) {
            Document document = documentRepository
                    .findByIdForUpdate(documentId)
                    .orElse(null);
            if (document == null
                    || !document.workspaceId().equals(workspaceId)
                    || document.reviewStatus() != DocumentReviewStatus.DRAFT) {
                stale = true;
            } else {
                documents.put(documentId, document);
            }
        }

        Map<UUID, DocumentBlock> blocks = new java.util.LinkedHashMap<>();
        List<Operation> blockOperations = operations.stream()
                .filter(operation -> operation.blockId() != null)
                .sorted(Comparator.comparing(
                        Operation::blockId
                ))
                .toList();
        for (Operation operation : blockOperations) {
            DocumentBlock block = blockRepository
                    .findByIdForUpdate(operation.blockId())
                    .orElse(null);
            if (block == null
                    || operation.documentId() == null
                    || !block.documentId().equals(operation.documentId())
                    || !Objects.equals(
                            block.version(),
                            operation.baseBlockVersion()
                    )) {
                stale = true;
            } else {
                blocks.put(block.id(), block);
            }
        }
        return new LockedTargets(documents, blocks, stale);
    }

    private void applyOperation(
            UUID workspaceId,
            UUID currentUserId,
            Operation operation,
            Map<UUID, UUID> createdDocuments,
            Map<UUID, DocumentBlock> lockedBlocks
    ) {
        switch (operation.operationType()) {
            case CREATE_DOCUMENT -> {
                Document created = documentService.create(
                        workspaceId,
                        currentUserId,
                        new CreateDocumentCommand(
                                operation.proposedParentDocumentId(),
                                operation.proposedDocumentTitle(),
                                operation.proposedDocumentType() == null
                                        ? DocumentType.REQUIREMENT
                                        : DocumentType.valueOf(
                                                operation.proposedDocumentType()
                                        )
                        )
                );
                createdDocuments.put(operation.id(), created.id());
            }
            case ADD_BLOCK -> blockService.create(
                    resolveDocumentId(operation, createdDocuments),
                    currentUserId,
                    new CreateDocumentBlockCommand(
                            DocumentBlockType.valueOf(
                                    operation.proposedBlockType()
                            ),
                            operation.proposedPlainText(),
                            operation.proposedContentSchemaVersion(),
                            readJson(operation.proposedContentJson())
                    )
            );
            case UPDATE_BLOCK -> {
                DocumentBlock block = lockedBlocks.get(operation.blockId());
                if (block == null) {
                    throw new IllegalStateException(
                            "预检后的 Block 锁定状态丢失"
                    );
                }
                blockService.updateContent(
                        operation.documentId(),
                        operation.blockId(),
                        currentUserId,
                        new UpdateDocumentBlockCommand(
                                operation.proposedPlainText(),
                                operation.proposedContentSchemaVersion(),
                                readJson(operation.proposedContentJson()),
                                operation.baseBlockVersion()
                        )
                );
            }
            case DELETE_BLOCK -> blockService.deleteWithVersion(
                    operation.documentId(),
                    operation.blockId(),
                    currentUserId,
                    operation.baseBlockVersion()
            );
        }
    }

    private UUID resolveDocumentId(
            Operation operation,
            Map<UUID, UUID> createdDocuments
    ) {
        if (operation.documentId() != null) {
            return operation.documentId();
        }
        UUID documentId = createdDocuments.get(
                operation.createdDocumentOperationId()
        );
        if (documentId == null) {
            throw new IllegalStateException(
                    "新建文档 Operation 尚未产生目标文档"
            );
        }
        return documentId;
    }

    private void recordDecision(
            ChangeRequest request,
            UUID currentUserId,
            String action,
            String message,
            Instant createdAt
    ) {
        operationLogRepository.save(new DocumentOperationLog(
                UUID.randomUUID(),
                request.workspaceId(),
                null,
                action,
                message,
                currentUserId,
                "DOCUMENT_CHANGE_REQUEST",
                request.id(),
                createdAt
        ));
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("workspaceId", request.workspaceId());
        payload.put("changeRequestId", request.id());
        payload.put("status", request.status().name());
        payload.put("reviewedBy", currentUserId);
        payload.put("reviewedAt", createdAt);
        outboxEventPublisher.publish(
                "DOCUMENT_CHANGE_REQUEST",
                request.id(),
                action,
                payload
        );
    }

    private DetailView replayedDetail(ChangeRequest request) {
        DetailView detail = detail(request);
        return new DetailView(
                detail.request(),
                detail.operations(),
                detail.requestEvidence(),
                true
        );
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

    private Operation buildOperation(
            UUID workspaceId,
            UUID requestId,
            CreateOperationCommand input,
            Map<String, Operation> savedByClientId
    ) {
        UUID createdDocumentOperationId = null;
        if (input.createdDocumentClientOperationId() != null) {
            Operation referenced = savedByClientId.get(
                    input.createdDocumentClientOperationId()
            );
            if (referenced == null
                    || referenced.operationType() != OperationType.CREATE_DOCUMENT) {
                throw operationInvalid(
                        "createdDocumentClientOperationId 必须引用更早的 CREATE_DOCUMENT"
                );
            }
            createdDocumentOperationId = referenced.id();
        }

        Document document = input.documentId() == null
                ? null
                : requireDocument(workspaceId, input.documentId());
        DocumentBlock block = input.blockId() == null
                ? null
                : requireBlock(document, input.blockId());
        String originalBlockType = null;
        String originalPlainText = null;
        Integer originalSchemaVersion = null;
        String originalContentJson = null;
        Integer originalSortOrder = null;
        String proposedPlainText = null;
        Integer proposedSchemaVersion = null;
        String proposedContentJson = null;

        switch (input.operationType()) {
            case CREATE_DOCUMENT -> {
                if (input.documentId() != null
                        || createdDocumentOperationId != null
                        || input.blockId() != null
                        || input.baseBlockVersion() != null) {
                    throw operationInvalid("CREATE_DOCUMENT 不能包含现有文档或 Block 目标");
                }
                requireText(input.proposedDocumentTitle(), 200, "文档标题");
                if (input.proposedParentDocumentId() != null) {
                    requireDocument(workspaceId, input.proposedParentDocumentId());
                }
            }
            case ADD_BLOCK -> {
                validateDocumentTarget(document, createdDocumentOperationId);
                if (input.blockId() != null || input.baseBlockVersion() != null) {
                    throw operationInvalid("ADD_BLOCK 不能包含 blockId 或 baseBlockVersion");
                }
                if (input.proposedBlockType() == null) {
                    throw operationInvalid("ADD_BLOCK 必须包含 proposedBlockType");
                }
                var normalized = contentCodec.normalize(
                        input.proposedBlockType(),
                        input.proposedPlainText(),
                        input.proposedContentSchemaVersion(),
                        input.proposedContent()
                );
                proposedPlainText = normalized.text();
                proposedSchemaVersion = normalized.schemaVersion();
                proposedContentJson = normalized.documentJson();
            }
            case UPDATE_BLOCK -> {
                if (document == null || block == null
                        || input.baseBlockVersion() == null) {
                    throw operationInvalid(
                            "UPDATE_BLOCK 必须包含 documentId、blockId 和 baseBlockVersion"
                    );
                }
                if (input.proposedBlockType() != null
                        && input.proposedBlockType() != block.type()) {
                    throw operationInvalid("UPDATE_BLOCK 不允许改变 Block 类型");
                }
                originalBlockType = block.type().name();
                originalPlainText = block.text();
                originalSchemaVersion = block.contentSchemaVersion();
                originalContentJson = block.contentJson();
                originalSortOrder = block.sortOrder();
                var normalized = contentCodec.normalize(
                        block.type(),
                        input.proposedPlainText(),
                        input.proposedContentSchemaVersion(),
                        input.proposedContent()
                );
                proposedPlainText = normalized.text();
                proposedSchemaVersion = normalized.schemaVersion();
                proposedContentJson = normalized.documentJson();
            }
            case DELETE_BLOCK -> {
                if (document == null || block == null
                        || input.baseBlockVersion() == null) {
                    throw operationInvalid(
                            "DELETE_BLOCK 必须包含 documentId、blockId 和 baseBlockVersion"
                    );
                }
                if (input.proposedPlainText() != null
                        || input.proposedContent() != null) {
                    throw operationInvalid("DELETE_BLOCK 不能包含建议正文");
                }
                originalBlockType = block.type().name();
                originalPlainText = block.text();
                originalSchemaVersion = block.contentSchemaVersion();
                originalContentJson = block.contentJson();
                originalSortOrder = block.sortOrder();
            }
        }

        return new Operation(
                UUID.randomUUID(),
                requestId,
                input.clientOperationId().trim(),
                input.sequenceNumber(),
                input.operationType(),
                input.documentId(),
                createdDocumentOperationId,
                input.blockId(),
                input.baseBlockVersion(),
                originalBlockType,
                originalPlainText,
                originalSchemaVersion,
                originalContentJson,
                originalSortOrder,
                trim(input.proposedDocumentTitle()),
                input.proposedDocumentType() == null
                        ? input.operationType() == OperationType.CREATE_DOCUMENT
                        ? DocumentType.REQUIREMENT.name() : null
                        : input.proposedDocumentType().name(),
                input.proposedParentDocumentId(),
                input.proposedBlockType() == null
                        ? null : input.proposedBlockType().name(),
                proposedPlainText,
                proposedSchemaVersion,
                proposedContentJson
        );
    }

    private Evidence buildEvidence(
            UUID workspaceId,
            UUID requestId,
            CreateEvidenceCommand input,
            Map<String, Operation> savedByClientId
    ) {
        requireText(input.description(), 1000, "Evidence description");
        RepositoryPathValidator.validate(input.filePath(), "Evidence 文件路径不合法");
        String filePath = RepositoryPathValidator.normalize(input.filePath());
        if ((input.startLine() == null) != (input.endLine() == null)
                || input.startLine() != null
                && (input.startLine() < 1
                || input.endLine() < input.startLine())) {
            throw evidenceInvalid("Evidence 行范围不合法");
        }
        Operation operation = input.clientOperationId() == null
                ? null
                : savedByClientId.get(input.clientOperationId());
        if (input.clientOperationId() != null && operation == null) {
            throw new DocumentChangeException(
                    HttpStatus.BAD_REQUEST,
                    "DOCUMENT_CHANGE_EVIDENCE_OPERATION_INVALID",
                    "Evidence 引用了不存在的 clientOperationId"
            );
        }
        GitRepository git = gitRepository.findRepositoryById(input.repositoryId())
                .filter(item -> item.workspaceId().equals(workspaceId))
                .orElseThrow(() -> evidenceInvalid("Evidence Repository 不属于工作区"));
        var file = gitRepository.findFileByRepositoryIdAndPath(
                        git.id(),
                        filePath
                )
                .orElseThrow(() -> evidenceInvalid("Evidence 文件不存在"));
        String excerpt = excerpt(
                file.contentText(),
                input.startLine(),
                input.endLine()
        );
        return new Evidence(
                UUID.randomUUID(),
                requestId,
                operation == null ? null : operation.id(),
                git.id(),
                trim(git.lastSyncedCommit()),
                filePath,
                input.startLine(),
                input.endLine(),
                input.description().trim(),
                file.blobSha(),
                excerpt,
                excerpt == null ? null : sha256(excerpt)
        );
    }

    private void validateCreateCommand(CreateCommand command) {
        if (command == null) {
            throw invalid("请求体不能为空");
        }
        requireText(command.clientRequestId(), 100, "clientRequestId");
        requireText(command.summary(), 300, "summary");
        requireText(command.rationale(), 10_000, "rationale");
        List<CreateOperationCommand> operations = safeList(command.operations());
        if (operations.isEmpty() || operations.size() > 50) {
            throw operationInvalid("Operations 数量必须在 1 到 50 之间");
        }
        if (safeList(command.evidence()).size() > 50) {
            throw evidenceInvalid("Evidence 数量不能超过 50");
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.Set<Integer> sequences = new java.util.HashSet<>();
        for (CreateOperationCommand operation : operations) {
            if (operation == null || operation.operationType() == null) {
                throw operationInvalid("Operation 类型不能为空");
            }
            requireText(operation.clientOperationId(), 100, "clientOperationId");
            if (!ids.add(operation.clientOperationId().trim())) {
                throw operationInvalid("clientOperationId 在请求内必须唯一");
            }
            if (operation.sequenceNumber() < 1
                    || !sequences.add(operation.sequenceNumber())) {
                throw operationInvalid("sequenceNumber 必须为唯一正整数");
            }
        }
        for (int sequence = 1; sequence <= operations.size(); sequence++) {
            if (!sequences.contains(sequence)) {
                throw operationInvalid("sequenceNumber 必须从 1 连续递增");
            }
        }
    }

    private Document requireDocument(UUID workspaceId, UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .filter(item -> item.workspaceId().equals(workspaceId))
                .orElseThrow(() -> operationInvalid("目标文档不存在或不属于工作区"));
        if (document.reviewStatus() != DocumentReviewStatus.DRAFT) {
            throw operationInvalid("目标文档当前不可编辑");
        }
        return document;
    }

    private DocumentBlock requireBlock(Document document, UUID blockId) {
        if (document == null) {
            throw operationInvalid("Block 操作必须指定 documentId");
        }
        return blockRepository.findById(blockId)
                .filter(item -> item.documentId().equals(document.id()))
                .orElseThrow(() -> operationInvalid("目标 Block 不属于指定文档"));
    }

    private void validateDocumentTarget(
            Document document,
            UUID createdDocumentOperationId
    ) {
        if ((document == null) == (createdDocumentOperationId == null)) {
            throw operationInvalid(
                    "ADD_BLOCK 必须且只能引用现有文档或本请求新建文档之一"
            );
        }
    }

    private CreateResult replay(ChangeRequest existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new DocumentChangeException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_CONFLICT",
                    "clientRequestId 已用于不同的变更请求"
            );
        }
        return new CreateResult(
                existing.id(),
                existing.status(),
                existing.createdAt(),
                true
        );
    }

    private String fingerprint(CreateCommand command) {
        try {
            return sha256(objectMapper.writeValueAsString(command));
        } catch (JsonProcessingException exception) {
            throw invalid("变更请求无法序列化");
        }
    }

    private String excerpt(
            String content,
            Integer startLine,
            Integer endLine
    ) {
        if (content == null) {
            return null;
        }
        String[] lines = content.split("\\R", -1);
        int start = startLine == null ? 1 : startLine;
        int end = endLine == null ? Math.min(lines.length, 200) : endLine;
        if (start > lines.length || end > lines.length || end - start + 1 > 200) {
            throw evidenceInvalid("Evidence 行范围超出文件或超过 200 行");
        }
        String value = String.join(
                "\n",
                java.util.Arrays.copyOfRange(lines, start - 1, end)
        );
        if (value.length() > 16_000) {
            throw evidenceInvalid("Evidence 代码片段不能超过 16000 字符");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireText(String value, int max, String field) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max) {
            throw invalid(field + " 必须为 1 到 " + max + " 个字符");
        }
    }

    private DocumentChangeException operationInvalid(String message) {
        return new DocumentChangeException(
                HttpStatus.BAD_REQUEST,
                "DOCUMENT_CHANGE_OPERATION_INVALID",
                message
        );
    }

    private DocumentChangeException evidenceInvalid(String message) {
        return new DocumentChangeException(
                HttpStatus.BAD_REQUEST,
                "DOCUMENT_CHANGE_EVIDENCE_INVALID",
                message
        );
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
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

    protected DocumentChangeException conflict(
            String code,
            String message
    ) {
        return new DocumentChangeException(
                HttpStatus.CONFLICT,
                code,
                message
        );
    }
}
