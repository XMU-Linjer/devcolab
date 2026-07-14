package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.common.outbox.application.OutboxEventPublisher;
import com.devcollab.knowledgecore.document.application.exception.DocumentBlockNotFoundException;
import com.devcollab.knowledgecore.document.application.exception.DocumentBlockVersionConflictException;
import com.devcollab.knowledgecore.document.application.exception.InvalidDocumentBlockPositionException;
import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.knowledgecore.document.domain.DocumentBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentBlockApplicationService {

    private final DocumentBlockRepository blockRepository;
    private final DocumentApplicationService documentService;
    private final OutboxEventPublisher outboxEventPublisher;

    public DocumentBlockApplicationService(
            DocumentBlockRepository blockRepository,
            DocumentApplicationService documentService,
            OutboxEventPublisher outboxEventPublisher
    ) {
        this.blockRepository = blockRepository;
        this.documentService = documentService;
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Transactional
    public synchronized DocumentBlock create(
            UUID documentId,
            UUID currentUserId,
            CreateDocumentBlockCommand command
    ) {
        Document document = documentService.get(documentId, currentUserId);

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
        DocumentBlock saved = blockRepository.save(block);
        documentService.logDocumentOperation(
                document,
                "DOCUMENT_BLOCK_CREATED",
                "新增 Block：" + saved.type().name(),
                currentUserId,
                "DOCUMENT_BLOCK",
                saved.id(),
                saved.createdAt()
        );
        publishBlockEvent(
                "DOCUMENT_BLOCK_CREATED",
                document,
                saved,
                currentUserId
        );
        return saved;
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
        Document document = documentService.get(documentId, currentUserId);

        requireBlock(documentId, blockId);
        DocumentBlock updated = blockRepository.updateTextIfVersionMatches(
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
        documentService.logDocumentOperation(
                document,
                "DOCUMENT_BLOCK_UPDATED",
                "更新 Block 内容",
                currentUserId,
                "DOCUMENT_BLOCK",
                updated.id(),
                updated.updatedAt()
        );
        publishBlockEvent(
                "DOCUMENT_BLOCK_UPDATED",
                document,
                updated,
                currentUserId
        );
        return updated;
    }

    @Transactional
    public synchronized void delete(
            UUID documentId,
            UUID blockId,
            UUID currentUserId
    ) {
        Document document = documentService.get(documentId, currentUserId);
        DocumentBlock block = requireBlock(documentId, blockId);

        blockRepository.deleteById(block.id());
        documentService.logDocumentOperation(
                document,
                "DOCUMENT_BLOCK_DELETED",
                "删除 Block：" + block.type().name(),
                currentUserId,
                "DOCUMENT_BLOCK",
                block.id(),
                Instant.now()
        );
        normalizeSortOrder(documentId);
        publishBlockEvent(
                "DOCUMENT_BLOCK_DELETED",
                document,
                block,
                currentUserId
        );
    }

    @Transactional
    public synchronized List<DocumentBlock> move(
            UUID documentId,
            UUID blockId,
            UUID currentUserId,
            MoveDocumentBlockCommand command
    ) {
        Document document = documentService.get(documentId, currentUserId);
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
        List<DocumentBlock> moved = saveNormalizedOrder(current);
        moved.stream()
                .filter(item -> item.id().equals(block.id()))
                .findFirst()
                .ifPresent(movedBlock -> publishBlockEvent(
                        "DOCUMENT_BLOCK_MOVED",
                        document,
                        movedBlock,
                        currentUserId
                ));
        documentService.logDocumentOperation(
                document,
                "DOCUMENT_BLOCK_MOVED",
                "调整 Block 排序到第 " + command.targetIndex() + " 位",
                currentUserId,
                "DOCUMENT_BLOCK",
                block.id(),
                Instant.now()
        );
        return moved;
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

    private void publishBlockEvent(
            String eventType,
            Document document,
            DocumentBlock block,
            UUID currentUserId
    ) {
        outboxEventPublisher.publish(
                "DOCUMENT_BLOCK",
                block.id(),
                eventType,
                blockPayload(document, block, currentUserId)
        );
    }

    private Map<String, Object> blockPayload(
            Document document,
            DocumentBlock block,
            UUID currentUserId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceId", document.workspaceId());
        payload.put("documentId", document.id());
        payload.put("documentTitle", document.title());
        payload.put("blockId", block.id());
        payload.put("type", block.type().name());
        payload.put("sortOrder", block.sortOrder());
        payload.put("version", block.version());
        payload.put("operatorUserId", currentUserId);
        payload.put("updatedAt", block.updatedAt());
        return payload;
    }
}
