package com.devcollab.knowledgecore.document.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentBlockRepository {

    DocumentBlock save(DocumentBlock block);

    List<DocumentBlock> saveAll(List<DocumentBlock> blocks);

    Optional<DocumentBlock> findById(UUID blockId);

    List<DocumentBlock> findAllByDocumentId(UUID documentId);

    Optional<DocumentBlock> updateTextIfVersionMatches(
            UUID blockId,
            String text,
            Instant updatedAt,
            long expectedVersion
    );

    void deleteById(UUID blockId);
}
