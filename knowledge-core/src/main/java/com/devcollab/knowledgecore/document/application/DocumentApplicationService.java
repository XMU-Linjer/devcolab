package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.application.exception.DocumentNotFoundException;
import com.devcollab.knowledgecore.document.application.exception.DocumentParentCycleException;
import com.devcollab.knowledgecore.document.application.exception.InvalidDocumentParentException;
import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentRepository;
import com.devcollab.knowledgecore.workspace.application.WorkspaceApplicationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentApplicationService {

    private final DocumentRepository documentRepository;
    private final WorkspaceApplicationService workspaceService;

    public DocumentApplicationService(
            DocumentRepository documentRepository,
            WorkspaceApplicationService workspaceService
    ) {
        this.documentRepository = documentRepository;
        this.workspaceService = workspaceService;
    }

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
        return documentRepository.save(document);
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
        return documentRepository.save(updated);
    }

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
        return documentRepository.save(moved);
    }

    public void delete(UUID documentId, UUID currentUserId) {
        Document document = requireDocument(documentId);
        workspaceService.requireMembership(
                document.workspaceId(),
                currentUserId
        );
        documentRepository.deleteById(documentId);
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
}
