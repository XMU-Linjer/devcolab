package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.application.exception.CollaborationOperationIdReusedException;
import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.knowledgecore.document.domain.DocumentCollaborationOperation;
import com.devcollab.knowledgecore.document.domain.DocumentCollaborationOperationPayload;
import com.devcollab.knowledgecore.document.domain.DocumentCollaborationOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentCollaborationOperationService {

    public static final String UPDATE_TEXT = "UPDATE_TEXT";
    public static final String CREATE_BLOCK = "CREATE_BLOCK";
    public static final String DELETE_BLOCK = "DELETE_BLOCK";
    public static final String MOVE_BLOCK = "MOVE_BLOCK";

    private final DocumentApplicationService documentService;
    private final DocumentBlockApplicationService blockService;
    private final DocumentCollaborationOperationRepository operationRepository;

    public DocumentCollaborationOperationService(
            DocumentApplicationService documentService,
            DocumentBlockApplicationService blockService,
            DocumentCollaborationOperationRepository operationRepository
    ) {
        this.documentService = documentService;
        this.blockService = blockService;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public DocumentCollaborationOperationResult apply(
            UUID documentId,
            UUID currentUserId,
            ApplyDocumentCollaborationOperationCommand command
    ) {
        validate(command);

        Document document = documentService.get(documentId, currentUserId);
        String fingerprint = fingerprint(command);

        // Serializes formal operations for one document across Core instances.
        // It also closes the race between duplicate lookup and result insert.
        operationRepository.lockDocument(documentId);

        var existing = operationRepository.findByClientOperationId(
                        documentId,
                        command.clientOperationId()
                );
        if (existing.isPresent()) {
            return duplicate(existing.get(), currentUserId, fingerprint);
        }

        documentService.ensureEditable(document);
        return applyFirst(
                documentId,
                currentUserId,
                command,
                fingerprint
        );
    }

    private DocumentCollaborationOperationResult applyFirst(
            UUID documentId,
            UUID currentUserId,
            ApplyDocumentCollaborationOperationCommand command,
            String fingerprint
    ) {
        DocumentCollaborationOperationPayload payload = switch (
                command.operationType()
        ) {
            case UPDATE_TEXT -> DocumentCollaborationOperationPayload.single(
                    blockService.updateContent(
                            documentId,
                            command.blockId(),
                            currentUserId,
                            new UpdateDocumentBlockCommand(
                                    command.text(),
                                    command.expectedVersion()
                            )
                    )
            );
            case CREATE_BLOCK -> DocumentCollaborationOperationPayload.single(
                    blockService.create(
                            documentId,
                            currentUserId,
                            new CreateDocumentBlockCommand(
                                    command.blockType(),
                                    command.text()
                            )
                    )
            );
            case DELETE_BLOCK -> {
                DocumentBlock deleted = blockService.deleteWithVersion(
                        documentId,
                        command.blockId(),
                        currentUserId,
                        command.expectedVersion()
                );
                yield DocumentCollaborationOperationPayload.ordered(
                        deleted,
                        blockService.list(documentId, currentUserId)
                );
            }
            case MOVE_BLOCK -> {
                List<DocumentBlock> blocks = blockService.move(
                        documentId,
                        command.blockId(),
                        currentUserId,
                        new MoveDocumentBlockCommand(command.targetIndex())
                );
                DocumentBlock moved = blocks.stream()
                        .filter(block -> block.id().equals(command.blockId()))
                        .findFirst()
                        .orElseThrow();
                yield DocumentCollaborationOperationPayload.ordered(
                        moved,
                        blocks
                );
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported collaboration operation: "
                            + command.operationType()
            );
        };
        long sequence = operationRepository.nextDocumentSequence(documentId);
        DocumentCollaborationOperation operation = operationRepository.save(
                new DocumentCollaborationOperation(
                        UUID.randomUUID(),
                        documentId,
                        sequence,
                        command.clientOperationId(),
                        command.operationType(),
                        currentUserId,
                        fingerprint,
                        payload,
                        Instant.now()
                )
        );
        return result(operation, "APPLIED");
    }

    private DocumentCollaborationOperationResult duplicate(
            DocumentCollaborationOperation existing,
            UUID currentUserId,
            String fingerprint
    ) {
        if (!existing.operatorUserId().equals(currentUserId)
                || !existing.requestFingerprint().equals(fingerprint)) {
            throw new CollaborationOperationIdReusedException();
        }
        return result(existing, "DUPLICATE");
    }

    private DocumentCollaborationOperationResult result(
            DocumentCollaborationOperation operation,
            String status
    ) {
        return new DocumentCollaborationOperationResult(
                operation.clientOperationId(),
                operation.result().block() == null
                        ? null
                        : operation.result().block().id(),
                operation.operationType(),
                status,
                operation.documentSequence(),
                operation.result().block(),
                operation.result().blocks()
        );
    }

    private void validate(ApplyDocumentCollaborationOperationCommand command) {
        if (command.operationType() == null || command.operationType().isBlank()) {
            throw new IllegalArgumentException("operationType is required");
        }
        switch (command.operationType()) {
            case UPDATE_TEXT -> {
                requireBlock(command);
                requireVersion(command);
                requireText(command);
            }
            case CREATE_BLOCK -> {
                if (command.blockType() == null) {
                    throw new IllegalArgumentException("blockType is required");
                }
                requireText(command);
            }
            case DELETE_BLOCK -> {
                requireBlock(command);
                requireVersion(command);
            }
            case MOVE_BLOCK -> {
                requireBlock(command);
                if (command.targetIndex() == null || command.targetIndex() < 0) {
                    throw new IllegalArgumentException(
                            "targetIndex must be zero or greater"
                    );
                }
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported collaboration operation: "
                            + command.operationType()
            );
        }
    }

    private void requireBlock(ApplyDocumentCollaborationOperationCommand command) {
        if (command.blockId() == null) {
            throw new IllegalArgumentException("blockId is required");
        }
    }

    private void requireVersion(ApplyDocumentCollaborationOperationCommand command) {
        if (command.expectedVersion() == null || command.expectedVersion() < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion must be zero or greater"
            );
        }
    }

    private void requireText(ApplyDocumentCollaborationOperationCommand command) {
        if (command.text() == null) {
            throw new IllegalArgumentException("content.text is required");
        }
    }

    private String fingerprint(
            ApplyDocumentCollaborationOperationCommand command
    ) {
        String canonical = command.blockId()
                + "\n" + command.operationType()
                + "\n" + command.expectedVersion()
                + "\n" + command.blockType()
                + "\n" + command.targetIndex()
                + "\n" + command.text();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
