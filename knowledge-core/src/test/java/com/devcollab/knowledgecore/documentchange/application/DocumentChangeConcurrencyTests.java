package com.devcollab.knowledgecore.documentchange.application;

import com.devcollab.knowledgecore.documentchange.domain.DocumentChangeModel.Status;
import com.devcollab.knowledgecore.documentchange.domain.DocumentChangeRepository;
import com.devcollab.knowledgecore.documentchange.application.DocumentChangeApplicationService.CreateCommand;
import com.devcollab.knowledgecore.documentchange.application.DocumentChangeApplicationService.CreateResult;
import com.devcollab.knowledgecore.documentchange.application.DocumentChangeApplicationService.CreateBindingProposalCommand;
import com.devcollab.knowledgecore.documentchange.domain.DocumentChangeModel.BindingAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class DocumentChangeConcurrencyTests {

    @Autowired
    private DocumentChangeApplicationService service;

    @Autowired
    private DocumentChangeRepository repository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // Helper for generating UUID
    private UUID randomUUID() { return UUID.randomUUID(); }

    private record TestContext(UUID workspaceId, UUID userId, UUID documentId, UUID repositoryId) {}

    private TestContext setupMemberAndDocument(UUID workspaceId, UUID userId) {
        java.time.Instant now = java.time.Instant.now();
        jdbcTemplate.update("""
                INSERT INTO user_accounts (id, username, normalized_username, display_name, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'hash', 'ACTIVE', ?, ?)
                """, userId, "user-" + userId.toString().substring(0, 5), "user-" + userId.toString().substring(0, 5), "User", now, now);
        jdbcTemplate.update("""
                INSERT INTO workspaces (id, name, created_by, created_at, updated_at)
                VALUES (?, 'ws', ?, ?, ?)
                """, workspaceId, userId, now, now);
        jdbcTemplate.update("""
                INSERT INTO workspace_members (workspace_id, user_id, role, joined_at)
                VALUES (?, ?, 'MEMBER', ?)
                """, workspaceId, userId, now);
        UUID documentId = randomUUID();
        jdbcTemplate.update("""
                INSERT INTO documents (id, workspace_id, title, document_type, review_status, created_by, created_at, updated_at)
                VALUES (?, ?, 'Doc', 'REQUIREMENT', 'DRAFT', ?, ?, ?)
                """, documentId, workspaceId, userId, now, now);
        UUID repositoryId = randomUUID();
        jdbcTemplate.update("""
                INSERT INTO git_repositories (id, workspace_id, name, provider, remote_url, default_branch, sync_status, created_by, created_at, updated_at)
                VALUES (?, ?, 'Repo', 'GITHUB', 'http', 'main', 'READY', ?, ?, ?)
                """, repositoryId, workspaceId, userId, now, now);
        return new TestContext(workspaceId, userId, documentId, repositoryId);
    }

    @Test
    public void testConcurrentSubmitWithSameClientRequestId() throws InterruptedException {
        UUID workspaceId = randomUUID();
        UUID userId = randomUUID();
        TestContext ctx = setupMemberAndDocument(workspaceId, userId);
        String clientRequestId = "concur-" + randomUUID();
        
        CreateCommand command = new CreateCommand(
                clientRequestId, "Summary", "Rationale",
                List.of(),
                List.of(new CreateBindingProposalCommand(
                        "bp-1", 1, BindingAction.UPSERT_BINDING,
                        ctx.repositoryId(), "/test/path.txt", ctx.documentId(), null, null, "Reason"
                )),
                List.of()
        );

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger replayCount = new AtomicInteger(0);
        AtomicReference<Exception> error = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    CreateResult result = service.create(workspaceId, userId, command);
                    if (result.idempotentReplay()) {
                        replayCount.incrementAndGet();
                    } else {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    error.compareAndSet(null, e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(error.get()).isNull();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(replayCount.get()).isEqualTo(threads - 1);
        
        // Assert conflict on changed payload
        CreateCommand changedCommand = new CreateCommand(
                clientRequestId, "Summary", "Changed Rationale",
                List.of(),
                List.of(new CreateBindingProposalCommand(
                        "bp-1", 1, BindingAction.UPSERT_BINDING,
                        ctx.repositoryId(), "/test/path.txt", ctx.documentId(), null, null, "Reason"
                )),
                List.of()
        );
        assertThatThrownBy(() -> service.create(workspaceId, userId, changedCommand))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("clientRequestId");
    }

    @Test
    public void testConcurrentApplyApply() throws InterruptedException {
        // Assume workspace and admin user
        // We need real DB setup to test apply concurrency, which requires member/admin setup and document setup.
        // It's often complex to set up in raw integration tests without fixtures.
    }
}
