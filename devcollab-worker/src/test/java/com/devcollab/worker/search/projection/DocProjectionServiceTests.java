package com.devcollab.worker.search.projection;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DocProjectionServiceTests {

    private final ElasticsearchSearchIndexGateway searchIndexGateway =
            mock(ElasticsearchSearchIndexGateway.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DocProjectionService projectionService =
            new DocProjectionService(searchIndexGateway, jdbcTemplate);

    @Test
    void missingRequiredPayloadFieldFailsWithReadableError() {
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> projectionService.project(
                eventId,
                "DOCUMENT_CREATED",
                "{\"documentId\":\"6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0\"}"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field")
                .hasMessageContaining("workspaceId");
    }

    @Test
    void invalidUuidPayloadFieldFailsWithReadableError() {
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> projectionService.project(
                eventId,
                "DOCUMENT_CREATED",
                """
                {
                  "workspaceId": "bad-uuid",
                  "documentId": "6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0",
                  "title": "示例文档",
                  "updatedAt": "2026-07-15T10:00:00Z"
                }
                """
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a UUID")
                .hasMessageContaining("workspaceId");
    }
}
