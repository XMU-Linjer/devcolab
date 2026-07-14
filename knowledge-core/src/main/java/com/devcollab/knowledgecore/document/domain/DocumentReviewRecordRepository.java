package com.devcollab.knowledgecore.document.domain;

import java.util.List;
import java.util.UUID;

public interface DocumentReviewRecordRepository {

    DocumentReviewRecord save(DocumentReviewRecord record);

    List<DocumentReviewRecord> findAllByDocumentId(UUID documentId);
}
