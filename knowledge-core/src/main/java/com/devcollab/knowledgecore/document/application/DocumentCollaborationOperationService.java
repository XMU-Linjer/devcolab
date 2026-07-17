package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.application.exception.CollaborationOperationIdReusedException;
import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.knowledgecore.document.domain.DocumentCollaborationOperation;
import com.devcollab.knowledgecore.document.domain.DocumentCollaborationOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DocumentCollaborationOperationService {

    public static final String UPDATE_TEXT = "UPDATE_TEXT";

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
        if (!UPDATE_TEXT.equals(command.operationType())) {
            throw new IllegalArgumentException(
                    "Unsupported collaboration operation: "
                            + command.operationType()
            );
        }

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
        DocumentBlock block = blockService.updateContent(
                documentId,
                command.blockId(),
                currentUserId,
                new UpdateDocumentBlockCommand(
                        command.text(),
                        command.expectedVersion()
                )
        );
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
                        block,
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
                operation.block().id(),
                operation.operationType(),
                status,
                operation.documentSequence(),
                operation.block()
        );
    }

    private String fingerprint(
            ApplyDocumentCollaborationOperationCommand command
    ) {
        String canonical = command.blockId()
                + "\n" + command.operationType()
                + "\n" + command.expectedVersion()
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
