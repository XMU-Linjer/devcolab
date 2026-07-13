package com.devcollab.knowledgecore.document.infrastructure;

import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.knowledgecore.document.domain.DocumentBlockRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryDocumentBlockRepository
        implements DocumentBlockRepository {

    private final Map<UUID, DocumentBlock> blocks =
            new ConcurrentHashMap<>();

    @Override
    public DocumentBlock save(DocumentBlock block) {
        blocks.put(block.id(), block);
        return block;
    }

    @Override
    public List<DocumentBlock> saveAll(List<DocumentBlock> documentBlocks) {
        documentBlocks.forEach(this::save);
        return documentBlocks;
    }

    @Override
    public Optional<DocumentBlock> findById(UUID blockId) {
        return Optional.ofNullable(blocks.get(blockId));
    }

    @Override
    public List<DocumentBlock> findAllByDocumentId(UUID documentId) {
        return blocks.values().stream()
                .filter(block -> block.documentId().equals(documentId))
                .sorted(Comparator.comparingInt(DocumentBlock::sortOrder))
                .toList();
    }

    @Override
    public void deleteById(UUID blockId) {
        blocks.remove(blockId);
    }
}
