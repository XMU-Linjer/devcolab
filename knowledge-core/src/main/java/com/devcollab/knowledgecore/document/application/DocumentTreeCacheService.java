package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.common.cache.CacheKey;
import com.devcollab.knowledgecore.common.cache.CacheProperties;
import com.devcollab.knowledgecore.common.cache.RedisCacheService;
import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DocumentTreeCacheService {

    private static final Logger log = LoggerFactory.getLogger(DocumentTreeCacheService.class);

    private final RedisCacheService cache;
    private final DocumentRepository documentRepository;
    private final CacheProperties properties;

    public DocumentTreeCacheService(
            RedisCacheService cache,
            DocumentRepository documentRepository,
            CacheProperties properties
    ) {
        this.cache = cache;
        this.documentRepository = documentRepository;
        this.properties = properties;
    }

    public List<Document> listTreeSource(UUID workspaceId) {
        String key = CacheKey.documentTree(workspaceId);
        Optional<List<Document>> cached = cache.get(key, DocumentList.class)
                .map(DocumentList::documents);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<Document> documents = documentRepository.findAllByWorkspaceId(workspaceId);
        cache.set(key, new DocumentList(documents), properties.documentTreeTtl());
        return documents;
    }

    public void evictTree(UUID workspaceId) {
        cache.evict(CacheKey.documentTree(workspaceId));
    }

    public record DocumentList(List<Document> documents) {
    }
}