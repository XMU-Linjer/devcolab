package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.application.exception.DocumentBlockNotFoundException;
import com.devcollab.knowledgecore.document.application.exception.DocumentBlockVersionConflictException;
import com.devcollab.knowledgecore.document.application.exception.InvalidDocumentBlockPositionException;
import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.knowledgecore.document.domain.DocumentBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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

    @Transactional
    public synchronized DocumentBlock create(
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
                0,
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

    @Transactional
    public synchronized DocumentBlock updateContent(
            UUID documentId,
            UUID blockId,
            UUID currentUserId,
            UpdateDocumentBlockCommand command
    ) {
        documentService.get(documentId, currentUserId);

        requireBlock(documentId, blockId);
        return blockRepository.updateTextIfVersionMatches(
                        blockId,
                        command.text().trim(),
                        Instant.now(),
                        command.expectedVersion()
                )
                .filter(found -> found.documentId().equals(documentId))
                .orElseThrow(() -> blockRepository.findById(blockId)
                        .filter(found -> found.documentId().equals(documentId))
                        .isPresent()
                        ? new DocumentBlockVersionConflictException()
                        : new DocumentBlockNotFoundException());
    }

    @Transactional
    public synchronized void delete(
            UUID documentId,
            UUID blockId,
            UUID currentUserId
    ) {
        documentService.get(documentId, currentUserId);
        DocumentBlock block = requireBlock(documentId, blockId);

        blockRepository.deleteById(block.id());
        normalizeSortOrder(documentId);
    }

    @Transactional
    public synchronized List<DocumentBlock> move(
            UUID documentId,
            UUID blockId,
            UUID currentUserId,
            MoveDocumentBlockCommand command
    ) {
        documentService.get(documentId, currentUserId);
        DocumentBlock block = requireBlock(documentId, blockId);
        List<DocumentBlock> current = new ArrayList<>(
                blockRepository.findAllByDocumentId(documentId)
        );

        if (command.targetIndex() < 0
                || command.targetIndex() >= current.size()) {
            throw new InvalidDocumentBlockPositionException();
        }

        current.removeIf(item -> item.id().equals(block.id()));
        current.add(command.targetIndex(), block);
        return saveNormalizedOrder(current);
    }

    private DocumentBlock requireBlock(UUID documentId, UUID blockId) {
        return blockRepository.findById(blockId)
                .filter(found -> found.documentId().equals(documentId))
                .orElseThrow(DocumentBlockNotFoundException::new);
    }

    private void normalizeSortOrder(UUID documentId) {
        saveNormalizedOrder(
                blockRepository.findAllByDocumentId(documentId)
        );
    }

    private List<DocumentBlock> saveNormalizedOrder(
            List<DocumentBlock> blocks
    ) {
        Instant now = Instant.now();
        List<DocumentBlock> normalized = new ArrayList<>(blocks.size());

        for (int index = 0; index < blocks.size(); index++) {
            DocumentBlock block = blocks.get(index);
            normalized.add(block.sortOrder() == index
                    ? block
                    : block.changeSortOrder(index, now));
        }

        return blockRepository.saveAll(normalized);
    }
}
