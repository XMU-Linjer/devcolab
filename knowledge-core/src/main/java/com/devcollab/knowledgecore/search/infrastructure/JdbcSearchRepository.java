package com.devcollab.knowledgecore.search.infrastructure;

import com.devcollab.knowledgecore.search.domain.SearchHit;
import com.devcollab.knowledgecore.search.domain.SearchHitType;
import com.devcollab.knowledgecore.search.domain.SearchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public class JdbcSearchRepository implements SearchRepository {

    private static final int SNIPPET_LIMIT = 120;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SearchHit> searchWorkspace(
            UUID workspaceId,
            String keyword,
            int limit
    ) {
        String pattern = "%" + keyword.toLowerCase() + "%";

        List<SearchHit> titleHits = jdbcTemplate.query("""
                        SELECT id, title, updated_at
                          FROM documents
                         WHERE workspace_id = ?
                           AND LOWER(title) LIKE ?
                        """,
                (rs, rowNum) -> new SearchHit(
                        SearchHitType.DOCUMENT_TITLE,
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        null,
                        rs.getString("title"),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                workspaceId,
                pattern
        );

        List<SearchHit> blockHits = jdbcTemplate.query("""
                        SELECT d.id AS document_id,
                               d.title AS document_title,
                               b.id AS block_id,
                               b.text AS block_text,
                               b.updated_at AS updated_at
                          FROM document_blocks b
                          JOIN documents d ON d.id = b.document_id
                         WHERE d.workspace_id = ?
                           AND LOWER(b.text) LIKE ?
                         ORDER BY b.sort_order, b.id
                        """,
                (rs, rowNum) -> new SearchHit(
                        SearchHitType.BLOCK_CONTENT,
                        rs.getObject("document_id", UUID.class),
                        rs.getString("document_title"),
                        rs.getObject("block_id", UUID.class),
                        snippet(rs.getString("block_text"), keyword),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                workspaceId,
                pattern
        );

        return Stream.concat(titleHits.stream(), blockHits.stream())
                .sorted(Comparator
                        .comparing(SearchHit::updatedAt, Comparator.reverseOrder())
                        .thenComparing(SearchHit::documentTitle)
                        .thenComparing(hit -> hit.type().name()))
                .limit(limit)
                .toList();
    }

    private static String snippet(String text, String keyword) {
        String normalizedText = text == null ? "" : text;
        if (normalizedText.length() <= SNIPPET_LIMIT) {
            return normalizedText;
        }

        String lowerText = normalizedText.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int hitIndex = lowerText.indexOf(lowerKeyword);
        if (hitIndex < 0) {
            return normalizedText.substring(0, SNIPPET_LIMIT) + "...";
        }

        int start = Math.max(0, hitIndex - 40);
        int end = Math.min(normalizedText.length(), start + SNIPPET_LIMIT);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < normalizedText.length() ? "..." : "";
        return prefix + normalizedText.substring(start, end) + suffix;
    }
}
