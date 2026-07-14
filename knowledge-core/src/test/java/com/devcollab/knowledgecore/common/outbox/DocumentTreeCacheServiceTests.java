package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.common.cache.CacheKey;
import com.devcollab.knowledgecore.common.cache.CacheProperties;
import com.devcollab.knowledgecore.common.cache.RedisCacheService;
import com.devcollab.knowledgecore.document.application.DocumentTreeCacheService.DocumentList;
import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentRepository;
import com.devcollab.knowledgecore.document.domain.DocumentReviewStatus;
import com.devcollab.knowledgecore.document.domain.DocumentType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentTreeCacheServiceTests {

    private final RedisCacheService cache = mock(RedisCacheService.class);
    private final DocumentRepository repository = mock(DocumentRepository.class);
    private final CacheProperties properties = new CacheProperties(true, Duration.ofMinutes(10), Duration.ofMinutes(5));
    private final DocumentTreeCacheService service = new DocumentTreeCacheService(
            cache,
            repository,
            properties
    );

    @Test
    void shouldReturnCachedTreeSourceWithoutQueryingRepository() {
        UUID workspaceId = UUID.randomUUID();
        List<Document> documents = List.of(document(workspaceId, "需求文档"));
        String key = CacheKey.documentTree(workspaceId);
        when(cache.get(key, DocumentList.class)).thenReturn(Optional.of(new DocumentList(documents)));

        List<Document> result = service.listTreeSource(workspaceId);

        assertThat(result).isEqualTo(documents);
        verify(repository, never()).findAllByWorkspaceId(workspaceId);
    }

    @Test
    void shouldLoadFromRepositoryAndWriteRedisOnCacheMiss() {
        UUID workspaceId = UUID.randomUUID();
        List<Document> documents = List.of(document(workspaceId, "接口文档"));
        String key = CacheKey.documentTree(workspaceId);
        when(cache.get(key, DocumentList.class)).thenReturn(Optional.empty());
        when(repository.findAllByWorkspaceId(workspaceId)).thenReturn(documents);

        List<Document> result = service.listTreeSource(workspaceId);

        assertThat(result).isEqualTo(documents);
        verify(cache).set(eq(key), eq(new DocumentList(documents)), eq(properties.documentTreeTtl()));
    }

    @Test
    void shouldEvictDocumentTreeKey() {
        UUID workspaceId = UUID.randomUUID();

        service.evictTree(workspaceId);

        verify(cache).evict(CacheKey.documentTree(workspaceId));
    }

    private static Document document(UUID workspaceId, String title) {
        Instant now = Instant.parse("2026-07-14T00:00:00Z");
        return new Document(
                UUID.randomUUID(),
                workspaceId,
                null,
                title,
                DocumentType.REQUIREMENT,
                DocumentReviewStatus.DRAFT,
                UUID.randomUUID(),
                now,
                now
        );
    }
}
