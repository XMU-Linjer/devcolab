package com.devcollab.knowledgecore.document.domain;

import java.util.List;
import java.util.UUID;

public interface DocumentVersionRepository {

    DocumentVersion save(DocumentVersion version);

    int nextVersionNo(UUID documentId);

    List<DocumentVersion> findAllByDocumentId(UUID documentId);
}
