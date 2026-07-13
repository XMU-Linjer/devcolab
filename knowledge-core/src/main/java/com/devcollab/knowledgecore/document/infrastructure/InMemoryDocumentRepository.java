package com.devcollab.knowledgecore.document.infrastructure;

import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryDocumentRepository implements DocumentRepository {

    private final Map<UUID, Document> documents = new ConcurrentHashMap<>();

    @Override
    public Document save(Document document) {
        documents.put(document.id(), document);
        return document;
    }

    @Override
    public Optional<Document> findById(UUID documentId) {
        return Optional.ofNullable(documents.get(documentId));
    }

    @Override
    public List<Document> findAllByWorkspaceId(UUID workspaceId) {
        return documents.values().stream()
                .filter(document -> document.workspaceId().equals(workspaceId))
                .sorted(Comparator.comparing(Document::createdAt))
                .toList();
    }
}
