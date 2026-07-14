package com.devcollab.knowledgecore.document.infrastructure;

import com.devcollab.knowledgecore.document.domain.DocumentOperationLog;
import com.devcollab.knowledgecore.document.domain.DocumentOperationLogRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("in-memory")
public class InMemoryDocumentOperationLogRepository
        implements DocumentOperationLogRepository {

    private final Map<UUID, DocumentOperationLog> operationLogs =
            new ConcurrentHashMap<>();

    @Override
    public DocumentOperationLog save(DocumentOperationLog operationLog) {
        operationLogs.put(operationLog.id(), operationLog);
        return operationLog;
    }

    @Override
    public List<DocumentOperationLog> findAllByDocumentId(UUID documentId) {
        return operationLogs.values().stream()
                .filter(log -> log.documentId() != null)
                .filter(log -> log.documentId().equals(documentId))
                .sorted(Comparator
                        .comparing(DocumentOperationLog::createdAt)
                        .reversed())
                .toList();
    }
}
