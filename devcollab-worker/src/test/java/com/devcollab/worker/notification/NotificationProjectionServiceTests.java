package com.devcollab.worker.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationProjectionServiceTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JdbcTemplate jdbcTemplate;
    private NotificationProjectionService projectionService;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        projectionService = new NotificationProjectionService(jdbcTemplate);

        jdbcTemplate.execute("""
                CREATE TABLE workspace_members (
                    workspace_id UUID NOT NULL,
                    user_id UUID NOT NULL,
                    role VARCHAR(30) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE documents (
                    id UUID PRIMARY KEY,
                    created_by UUID NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE notifications (
                    id UUID PRIMARY KEY,
                    recipient_user_id UUID NOT NULL,
                    workspace_id UUID NOT NULL,
                    document_id UUID,
                    type VARCHAR(60) NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    content TEXT,
                    source_event_id UUID NOT NULL,
                    read_at TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    UNIQUE(recipient_user_id, source_event_id)
                )
                """);
    }

    @Test
    void reviewRequestedNotifiesWorkspaceAdminsExceptOperator()
            throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        insertMember(workspaceId, operatorId, "ADMIN");
        insertMember(workspaceId, adminId, "ADMIN");
        insertMember(workspaceId, memberId, "MEMBER");

        projectionService.project(
                UUID.randomUUID(),
                "REVIEW_REQUESTED",
                OBJECT_MAPPER.readTree("""
                        {
                          "workspaceId": "%s",
                          "documentId": "%s",
                          "title": "接口设计",
                          "operatorUserId": "%s"
                        }
                        """.formatted(workspaceId, documentId, operatorId)),
                Instant.now()
        );

        assertThat(countNotifications()).isEqualTo(1);
        assertThat(firstRecipient()).isEqualTo(adminId);
        assertThat(firstTitle()).isEqualTo("文档待评审：接口设计");
    }

    @Test
    void reviewCompletedNotifiesDocumentAuthor() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        insertDocument(documentId, authorId);

        projectionService.project(
                UUID.randomUUID(),
                "REVIEW_COMPLETED",
                OBJECT_MAPPER.readTree("""
                        {
                          "workspaceId": "%s",
                          "documentId": "%s",
                          "title": "登录需求",
                          "operatorUserId": "%s"
                        }
                        """.formatted(workspaceId, documentId, operatorId)),
                Instant.now()
        );

        assertThat(countNotifications()).isEqualTo(1);
        assertThat(firstRecipient()).isEqualTo(authorId);
        assertThat(firstType()).isEqualTo("REVIEW_COMPLETED");
        assertThat(firstTitle()).isEqualTo("文档已发布：登录需求");
    }

    @Test
    void reviewIssueCreatedNotifiesAssignee() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        projectionService.project(
                UUID.randomUUID(),
                "REVIEW_ISSUE_CREATED",
                OBJECT_MAPPER.readTree("""
                        {
                          "workspaceId": "%s",
                          "documentId": "%s",
                          "title": "缺少边界说明",
                          "assigneeId": "%s",
                          "operatorUserId": "%s"
                        }
                        """.formatted(
                        workspaceId,
                        documentId,
                        assigneeId,
                        operatorId
                )),
                Instant.now()
        );

        assertThat(countNotifications()).isEqualTo(1);
        assertThat(firstRecipient()).isEqualTo(assigneeId);
        assertThat(firstType()).isEqualTo("REVIEW_ISSUE_CREATED");
    }

    @Test
    void duplicateEventDoesNotCreateDuplicateNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        insertMember(workspaceId, adminId, "ADMIN");

        var payload = OBJECT_MAPPER.readTree("""
                {
                  "workspaceId": "%s",
                  "documentId": "%s",
                  "title": "版本发布",
                  "operatorUserId": "%s"
                }
                """.formatted(workspaceId, documentId, operatorId));

        projectionService.project(
                eventId,
                "REVIEW_REQUESTED",
                payload,
                Instant.now()
        );
        projectionService.project(
                eventId,
                "REVIEW_REQUESTED",
                payload,
                Instant.now()
        );

        assertThat(countNotifications()).isEqualTo(1);
        assertThat(firstRecipient()).isEqualTo(adminId);
    }

    private void insertMember(UUID workspaceId, UUID userId, String role) {
        jdbcTemplate.update("""
                        INSERT INTO workspace_members
                            (workspace_id, user_id, role)
                        VALUES (?, ?, ?)
                        """,
                workspaceId,
                userId,
                role
        );
    }

    private void insertDocument(UUID documentId, UUID createdBy) {
        jdbcTemplate.update("""
                        INSERT INTO documents (id, created_by)
                        VALUES (?, ?)
                        """,
                documentId,
                createdBy
        );
    }

    private Integer countNotifications() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications",
                Integer.class
        );
    }

    private UUID firstRecipient() {
        return jdbcTemplate.queryForObject(
                "SELECT recipient_user_id FROM notifications LIMIT 1",
                UUID.class
        );
    }

    private String firstType() {
        return jdbcTemplate.queryForObject(
                "SELECT type FROM notifications LIMIT 1",
                String.class
        );
    }

    private String firstTitle() {
        return jdbcTemplate.queryForObject(
                "SELECT title FROM notifications LIMIT 1",
                String.class
        );
    }
}
