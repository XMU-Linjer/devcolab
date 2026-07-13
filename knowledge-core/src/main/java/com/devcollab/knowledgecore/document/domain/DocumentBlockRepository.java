package com.devcollab.knowledgecore.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentBlockRepository {

    DocumentBlock save(DocumentBlock block);

    Optional<DocumentBlock> findById(UUID blockId);

    List<DocumentBlock> findAllByDocumentId(UUID documentId);
}
