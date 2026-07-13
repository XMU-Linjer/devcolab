package com.devcollab.knowledgecore.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(UUID documentId);

    List<Document> findAllByWorkspaceId(UUID workspaceId);
}
