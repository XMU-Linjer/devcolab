package com.devcollab.knowledgecore.document.domain;

import java.util.Optional;
import java.util.UUID;

public interface DocumentCollaborationOperationRepository {

    Optional<DocumentCollaborationOperation> findByClientOperationId(
            UUID documentId,
            UUID clientOperationId
    );

    void lockDocument(UUID documentId);

    long nextDocumentSequence(UUID documentId);

    DocumentCollaborationOperation save(
            DocumentCollaborationOperation operation
    );
}
