package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.application.exception.DocumentBlockNotFoundException;
import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.knowledgecore.document.domain.DocumentBlockRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentBlockApplicationService {

    private final DocumentBlockRepository blockRepository;
    private final DocumentApplicationService documentService;

    public DocumentBlockApplicationService(
            DocumentBlockRepository blockRepository,
            DocumentApplicationService documentService
    ) {
        this.blockRepository = blockRepository;
        this.documentService = documentService;
    }

    public DocumentBlock create(
            UUID documentId,
            UUID currentUserId,
            CreateDocumentBlockCommand command
    ) {
        documentService.get(documentId, currentUserId);

        int sortOrder = blockRepository
                .findAllByDocumentId(documentId)
                .size();
        Instant now = Instant.now();
        DocumentBlock block = new DocumentBlock(
                UUID.randomUUID(),
                documentId,
                command.type(),
                command.text().trim(),
                sortOrder,
                currentUserId,
                now,
                now
        );
        return blockRepository.save(block);
    }

    public List<DocumentBlock> list(
            UUID documentId,
            UUID currentUserId
    ) {
        documentService.get(documentId, currentUserId);
        return blockRepository.findAllByDocumentId(documentId);
    }

    public DocumentBlock updateContent(
            UUID documentId,
            UUID blockId,
            UUID currentUserId,
            UpdateDocumentBlockCommand command
    ) {
        documentService.get(documentId, currentUserId);

        DocumentBlock block = blockRepository.findById(blockId)
                .filter(found -> found.documentId().equals(documentId))
                .orElseThrow(DocumentBlockNotFoundException::new);
        DocumentBlock updated = block.updateText(
                command.text().trim(),
                Instant.now()
        );
        return blockRepository.save(updated);
    }
}
