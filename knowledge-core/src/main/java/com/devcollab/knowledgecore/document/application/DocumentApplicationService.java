package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.application.exception.DocumentNotFoundException;
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

    private Document requireDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(DocumentNotFoundException::new);
    }
}
