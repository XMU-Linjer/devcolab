package com.devcollab.knowledgecore.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository {

    DocumentVersion save(DocumentVersion version);

    int nextVersionNo(UUID documentId);

    Optional<DocumentVersion> findById(UUID versionId);

    List<DocumentVersion> findAllByDocumentId(UUID documentId);
}
