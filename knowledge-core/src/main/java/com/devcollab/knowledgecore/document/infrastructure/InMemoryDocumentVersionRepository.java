package com.devcollab.knowledgecore.document.infrastructure;

import com.devcollab.knowledgecore.document.domain.DocumentVersion;
import com.devcollab.knowledgecore.document.domain.DocumentVersionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("in-memory")
public class InMemoryDocumentVersionRepository implements DocumentVersionRepository {

    private final Map<UUID, DocumentVersion> versions =
            new ConcurrentHashMap<>();

    @Override
    public DocumentVersion save(DocumentVersion version) {
        versions.put(version.id(), version);
        return version;
    }

    @Override
    public int nextVersionNo(UUID documentId) {
        return versions.values().stream()
                .filter(version -> version.documentId().equals(documentId))
                .mapToInt(DocumentVersion::versionNo)
                .max()
                .orElse(0) + 1;
    }

    @Override
    public List<DocumentVersion> findAllByDocumentId(UUID documentId) {
        return versions.values().stream()
                .filter(version -> version.documentId().equals(documentId))
                .sorted(Comparator
                        .comparing(DocumentVersion::versionNo)
                        .reversed())
                .toList();
    }
}
