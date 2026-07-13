package com.devcollab.knowledgecore.document.domain;

import java.util.List;
import java.util.UUID;

public interface DocumentBlockRepository {

    DocumentBlock save(DocumentBlock block);

    List<DocumentBlock> findAllByDocumentId(UUID documentId);
}
