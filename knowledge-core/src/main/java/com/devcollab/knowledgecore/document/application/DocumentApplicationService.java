package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.common.outbox.application.OutboxEventPublisher;
import com.devcollab.knowledgecore.document.application.exception.DocumentNotFoundException;
import com.devcollab.knowledgecore.document.application.exception.DocumentParentCycleException;
import com.devcollab.knowledgecore.document.application.exception.InvalidDocumentParentException;
import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentRepository;
import com.devcollab.knowledgecore.workspace.application.WorkspaceApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentApplicationService {

    private final DocumentRepository documentRepository;
    private final WorkspaceApplicationService workspaceService;
    private final OutboxEventPublisher outboxEventPublisher;

    public DocumentApplicationService(
            DocumentRepository documentRepository,
            WorkspaceApplicationService workspaceService,
            OutboxEventPublisher outboxEventPublisher
    ) {
        this.documentRepository = documentRepository;
        this.workspaceService = workspaceService;
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Transactional
    public Document create(
            UUID workspaceId,
            UUID currentUserId,
            CreateDocumentCommand command
    ) {
        workspaceService.requireMembership(workspaceId, currentUserId);
        validateParent(workspaceId, command.parentDocumentId());

        Instant now = Instant.now();
        Document document = new Document(
                UUID.randomUUID(),
                workspaceId,
                command.parentDocumentId(),
                command.title().trim(),
                currentUserId,
                now,
                now
        );
        Document saved = documentRepository.save(document);
        publishDocumentEvent("DOCUMENT_CREATED", saved, currentUserId);
        return saved;
    }

    public List<Document> listTreeSource(
            UUID workspaceId,
            UUID currentUserId
    ) {
        workspaceService.requireMembership(workspaceId, currentUserId);
        return documentRepository.findAllByWorkspaceId(workspaceId);
    }

    public Document get(UUID documentId, UUID currentUserId) {
        Document document = requireDocument(documentId);
        workspaceService.requireMembership(
                document.workspaceId(),
                currentUserId
        );
        return document;
    }

    @Transactional
    public Document update(
            UUID documentId,
            UUID currentUserId,
            UpdateDocumentCommand command
    ) {
        Document document = requireDocument(documentId);
        workspaceService.requireMembership(
                document.workspaceId(),
                currentUserId
        );

        Document updated = new Document(
                document.id(),
                document.workspaceId(),
                document.parentDocumentId(),
                command.title().trim(),
                document.createdBy(),
                document.createdAt(),
                Instant.now()
        );
        Document saved = documentRepository.save(updated);
        publishDocumentEvent("DOCUMENT_UPDATED", saved, currentUserId);
        return saved;
    }

    @Transactional
    public Document move(
            UUID documentId,
            UUID currentUserId,
            MoveDocumentCommand command
    ) {
        Document document = requireDocument(documentId);
        workspaceService.requireMembership(
                document.workspaceId(),
                currentUserId
        );
        validateParent(document.workspaceId(), command.parentDocumentId());
        validateNoCycle(document, command.parentDocumentId());

        Document moved = new Document(
                document.id(),
                document.workspaceId(),
                command.parentDocumentId(),
                document.title(),
                document.createdBy(),
                document.createdAt(),
                Instant.now()
        );
        Document saved = documentRepository.save(moved);
        publishDocumentEvent("DOCUMENT_MOVED", saved, currentUserId);
        return saved;
    }

    @Transactional
    public void delete(UUID documentId, UUID currentUserId) {
        Document document = requireDocument(documentId);
        workspaceService.requireMembership(
                document.workspaceId(),
                currentUserId
        );
        documentRepository.deleteById(documentId);
        publishDocumentEvent("DOCUMENT_DELETED", document, currentUserId);
    }

    private void validateParent(
            UUID workspaceId,
            UUID parentDocumentId
    ) {
        if (parentDocumentId == null) {
            return;
        }

        Document parent = requireDocument(parentDocumentId);
        if (!parent.workspaceId().equals(workspaceId)) {
            throw new InvalidDocumentParentException();
        }
    }

    private void validateNoCycle(Document document, UUID parentDocumentId) {
        if (parentDocumentId == null) {
            return;
        }
        if (document.id().equals(parentDocumentId)) {
            throw new DocumentParentCycleException();
        }

        List<Document> documents = documentRepository.findAllByWorkspaceId(
                document.workspaceId()
        );
        UUID cursor = parentDocumentId;
        while (cursor != null) {
            UUID current = cursor;
            if (document.id().equals(current)) {
                throw new DocumentParentCycleException();
            }
            Document parent = documents.stream()
                    .filter(candidate -> candidate.id().equals(current))
                    .findFirst()
                    .orElse(null);
            cursor = parent == null ? null : parent.parentDocumentId();
        }
    }

    private Document requireDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(DocumentNotFoundException::new);
    }

    private void publishDocumentEvent(
            String eventType,
            Document document,
            UUID currentUserId
    ) {
        outboxEventPublisher.publish(
                "DOCUMENT",
                document.id(),
                eventType,
                documentPayload(document, currentUserId)
        );
    }

    private Map<String, Object> documentPayload(
            Document document,
            UUID currentUserId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceId", document.workspaceId());
        payload.put("documentId", document.id());
        payload.put("parentDocumentId", document.parentDocumentId());
        payload.put("title", document.title());
        payload.put("operatorUserId", currentUserId);
        payload.put("updatedAt", document.updatedAt());
        return payload;
    }
}
