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

    Optional<DocumentBlock> updateContentIfVersionMatches(
            UUID blockId,
            String text,
            int contentSchemaVersion,
            String contentJson,
            Instant updatedAt,
            long expectedVersion
    );

    boolean deleteIfVersionMatches(UUID blockId, long expectedVersion);

    void deleteById(UUID blockId);
}
