package com.devcollab.knowledgecore.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentBlockRepository {

    DocumentBlock save(DocumentBlock block);

    List<DocumentBlock> saveAll(List<DocumentBlock> blocks);

    Optional<DocumentBlock> findById(UUID blockId);

    List<DocumentBlock> findAllByDocumentId(UUID documentId);

    void deleteById(UUID blockId);
}
