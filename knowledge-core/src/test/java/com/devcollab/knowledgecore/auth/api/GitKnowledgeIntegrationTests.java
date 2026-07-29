package com.devcollab.knowledgecore.auth.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
class GitKnowledgeIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCompleteRepositoryChangeDiffAndDocumentBindingLoop() throws Exception {
        AuthSession admin = register();
        String workspaceId = createWorkspace(admin.token());
        String documentId = createDocument(admin.token(), workspaceId);
        String blockId = createBlock(admin.token(), documentId);
        String repositoryId = registerRepository(admin.token(), workspaceId);

        mockMvc.perform(post("/api/v1/documents/{id}/code-bindings", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "repositoryId":"%s",
                                  "blockId":"%s",
                                  "pathPattern":"knowledge-core/src/main/java/**"
                                }
                                """.formatted(repositoryId, blockId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pathPattern")
                        .value("knowledge-core/src/main/java/**"));

        String changeId = ingestChange(admin.token(), workspaceId, repositoryId)
                .get("id").asText();

        Integer gitEvents = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM outbox_events
                 WHERE aggregate_type = 'GIT_CHANGE'
                   AND aggregate_id = ?
                   AND event_type = 'GIT_CHANGE_SYNCED'
                """, Integer.class, UUID.fromString(changeId));
        assertThat(gitEvents).isEqualTo(1);

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/git/changes/{changeId}/affected-documents",
                        workspaceId,
                        changeId
                ).header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(documentId))
                .andExpect(jsonPath("$[0].blockId").value(blockId))
                .andExpect(jsonPath("$[0].matchedPaths[0]")
                        .value("knowledge-core/src/main/java/App.java"));
    }

    @Test
    void shouldTreatRepeatedExternalChangeAsIdempotent() throws Exception {
        AuthSession admin = register();
        String workspaceId = createWorkspace(admin.token());
        String repositoryId = registerRepository(admin.token(), workspaceId);

        String firstId = ingestChange(admin.token(), workspaceId, repositoryId)
                .get("id").asText();
        MvcResult duplicate = mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/git/repositories/{repositoryId}/changes",
                        workspaceId,
                        repositoryId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.duplicate").value(true))
                .andReturn();

        if (!responseJson(duplicate).get("duplicate").asBoolean()) {
            throw new AssertionError("duplicate marker expected");
        }
    }

    @Test
    void shouldAllowMemberToReadButRejectRepositoryManagement() throws Exception {
        AuthSession admin = register();
        AuthSession member = register();
        String workspaceId = createWorkspace(admin.token());
        inviteMember(admin.token(), workspaceId, member.username());
        registerRepository(admin.token(), workspaceId);

        mockMvc.perform(get("/api/v1/workspaces/{id}/git/repositories", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("DevCollab Core"));

        mockMvc.perform(post("/api/v1/workspaces/{id}/git/repositories", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(repositoryBody("https://example.com/member/repo.git")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void shouldExposeCodeGraphToMembersAndRejectOutsiders() throws Exception {
        AuthSession admin = register();
        AuthSession member = register();
        AuthSession outsider = register();
        String workspaceId = createWorkspace(admin.token());
        inviteMember(admin.token(), workspaceId, member.username());
        String repositoryId = registerRepository(admin.token(), workspaceId);
        UUID repositoryUuid = UUID.fromString(repositoryId);
        String apiPath = "src/main/java/demo/api/OrderPort.java";
        String servicePath = "src/main/java/demo/service/OrderService.java";

        jdbcTemplate.update("""
                INSERT INTO git_repository_files
                    (id, repository_id, path, blob_sha, size_bytes, language,
                     content_text)
                VALUES (?, ?, ?, 'service-blob', 62, 'Java', ?)
                """, UUID.randomUUID(), repositoryUuid, servicePath,
                "package demo.service;\nclass OrderService implements OrderPort {}\n");

        jdbcTemplate.update("""
                INSERT INTO code_symbols
                    (id, repository_id, file_path, symbol_key, language,
                     symbol_kind, qualified_name, simple_name, signature,
                     start_line, end_line)
                VALUES (?, ?, ?, 'java:demo.api.OrderPort', 'JAVA',
                        'INTERFACE', 'demo.api.OrderPort', 'OrderPort',
                        'INTERFACE demo.api.OrderPort', 1, 3)
                """, UUID.randomUUID(), repositoryUuid, apiPath);
        jdbcTemplate.update("""
                INSERT INTO code_symbols
                    (id, repository_id, file_path, symbol_key, language,
                     symbol_kind, qualified_name, simple_name, signature,
                     start_line, end_line)
                VALUES (?, ?, ?, 'java:demo.service.OrderService', 'JAVA',
                        'CLASS', 'demo.service.OrderService', 'OrderService',
                        'CLASS demo.service.OrderService', 1, 8)
                """, UUID.randomUUID(), repositoryUuid, servicePath);
        jdbcTemplate.update("""
                INSERT INTO code_symbol_dependencies
                    (id, repository_id, source_symbol_key, target_symbol_key,
                     relation_type, evidence_file_path)
                VALUES (?, ?, 'java:demo.service.OrderService',
                        'java:demo.api.OrderPort', 'IMPLEMENTS', ?)
                """, UUID.randomUUID(), repositoryUuid, servicePath);
        jdbcTemplate.update("""
                INSERT INTO code_file_dependencies
                    (id, repository_id, source_path, target_path, relation_type)
                VALUES (?, ?, ?, ?, 'IMPORTS')
                """, UUID.randomUUID(), repositoryUuid, servicePath, apiPath);

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/git/repositories/{repositoryId}/code-graph",
                        workspaceId, repositoryId)
                        .queryParam("filePath", servicePath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbols[0].symbolKey")
                        .value("java:demo.service.OrderService"))
                .andExpect(jsonPath("$.symbolDependencies[0].relationType")
                        .value("IMPLEMENTS"))
                .andExpect(jsonPath("$.fileDependencies[0].targetPath")
                        .value(apiPath));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/git/repositories/{repositoryId}/source",
                        workspaceId, repositoryId)
                        .queryParam("path", servicePath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(servicePath))
                .andExpect(jsonPath("$.readable").value(true))
                .andExpect(jsonPath("$.content").value(
                        "package demo.service;\nclass OrderService implements OrderPort {}\n"
                ))
                .andExpect(jsonPath("$.symbols[0].qualifiedName")
                        .value("demo.service.OrderService"));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/git/repositories/{repositoryId}/code-graph",
                        workspaceId, repositoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/git/repositories/{repositoryId}/source",
                        workspaceId, repositoryId)
                        .queryParam("path", servicePath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void shouldRejectBindingBlockFromAnotherDocument() throws Exception {
        AuthSession admin = register();
        String workspaceId = createWorkspace(admin.token());
        String firstDocument = createDocument(admin.token(), workspaceId);
        String secondDocument = createDocument(admin.token(), workspaceId);
        String foreignBlock = createBlock(admin.token(), secondDocument);
        String repositoryId = registerRepository(admin.token(), workspaceId);

        mockMvc.perform(post("/api/v1/documents/{id}/code-bindings", firstDocument)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryId":"%s","blockId":"%s","pathPattern":"src/**"}
                                """.formatted(repositoryId, foreignBlock)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GIT_BINDING_INVALID"));
    }

    @Test
    void shouldCreateAndQueryLegacyRangeAndSymbolBindings() throws Exception {
        AuthSession admin = register();
        String workspaceId = createWorkspace(admin.token());
        String documentId = createDocument(admin.token(), workspaceId);
        String blockId = createBlock(admin.token(), documentId);
        String repositoryId = registerRepository(admin.token(), workspaceId);
        String path = "src/main/java/demo/OrderService.java";

        createBinding(admin.token(), documentId, """
                {
                  "repositoryId":"%s",
                  "pathPattern":"%s"
                }
                """.formatted(repositoryId, path))
                .andExpect(jsonPath("$.anchorKind").value("FILE"))
                .andExpect(jsonPath("$.revision").isEmpty())
                .andExpect(jsonPath("$.blockId").isEmpty());

        createBinding(admin.token(), documentId, """
                {
                  "repositoryId":"%s",
                  "pathPattern":"%s",
                  "revision":"A",
                  "anchorKind":"FILE"
                }
                """.formatted(repositoryId, path))
                .andExpect(jsonPath("$.revision").value("A"));

        createBinding(admin.token(), documentId, """
                {
                  "repositoryId":"%s",
                  "pathPattern":"%s",
                  "revision":"A",
                  "anchorKind":"RANGE",
                  "startLine":10,
                  "endLine":20
                }
                """.formatted(repositoryId, path))
                .andExpect(jsonPath("$.targetKey").value("DOCUMENT"));

        createBinding(admin.token(), documentId, """
                {
                  "repositoryId":"%s",
                  "blockId":"%s",
                  "pathPattern":"%s",
                  "revision":"A",
                  "anchorKind":"RANGE",
                  "startLine":10,
                  "endLine":20
                }
                """.formatted(repositoryId, blockId, path))
                .andExpect(jsonPath("$.blockId").value(blockId));

        createBinding(admin.token(), documentId, """
                {
                  "repositoryId":"%s",
                  "blockId":"%s",
                  "pathPattern":"%s",
                  "revision":"A",
                  "anchorKind":"RANGE",
                  "startLine":21,
                  "endLine":30
                }
                """.formatted(repositoryId, blockId, path))
                .andExpect(status().isCreated());

        createBinding(admin.token(), documentId, """
                {
                  "repositoryId":"%s",
                  "blockId":"%s",
                  "pathPattern":"%s",
                  "revision":"A",
                  "anchorKind":"SYMBOL",
                  "symbolKey":"java:demo.OrderService#create",
                  "startLine":31,
                  "endLine":45
                }
                """.formatted(repositoryId, blockId, path))
                .andExpect(status().isCreated());

        createBinding(admin.token(), documentId, """
                {
                  "repositoryId":"%s",
                  "blockId":"%s",
                  "pathPattern":"%s",
                  "revision":"A",
                  "anchorKind":"SYMBOL",
                  "symbolKey":"java:demo.OrderService#cancel"
                }
                """.formatted(repositoryId, blockId, path))
                .andExpect(status().isCreated());

        createBinding(admin.token(), documentId, """
                {
                  "repositoryId":"%s",
                  "pathPattern":"%s",
                  "revision":"B",
                  "anchorKind":"FILE"
                }
                """.formatted(repositoryId, path))
                .andExpect(status().isCreated());

        MvcResult withLegacy = mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/repositories/{repositoryId}/code-bindings",
                        workspaceId, repositoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .queryParam("filePath", path)
                        .queryParam("revision", "A")
                        .queryParam("includeLegacy", "true")
                        .queryParam("maxBindings", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindings[0].revision").value("A"))
                .andReturn();
        JsonNode withLegacyJson = responseJson(withLegacy);
        assertThat(withLegacyJson.get("bindings")).hasSize(7);
        assertThat(withLegacyJson.get("bindings").findValuesAsText("revision"))
                .allMatch(value -> value.equals("null") || value.equals("A"));

        MvcResult exactOnly = mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/repositories/{repositoryId}/code-bindings",
                        workspaceId, repositoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .queryParam("filePath", path)
                        .queryParam("revision", "A")
                        .queryParam("includeLegacy", "false"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(responseJson(exactOnly).get("bindings")).hasSize(6);

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/repositories/{repositoryId}/code-bindings/batch",
                        workspaceId, repositoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "filePaths":["%s"],
                                  "revision":"A",
                                  "includeLegacy":false,
                                  "maxBindings":2
                                }
                                """.formatted(path)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].bindings.length()").value(2))
                .andExpect(jsonPath("$.files[0].bindings[0].revision").value("A"))
                .andExpect(jsonPath("$.files[0].truncated").value(true))
                .andExpect(jsonPath("$.files[0].omittedBindingCount").value(4));

        mockMvc.perform(get("/api/v1/documents/{documentId}/code-bindings", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .queryParam("revision", "A")
                        .queryParam("includeLegacy", "false")
                        .queryParam("blockId", blockId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].repositoryId").value(repositoryId))
                .andExpect(jsonPath("$[0].blockId").value(blockId))
                .andExpect(jsonPath("$[0].anchorKind").exists())
                .andExpect(jsonPath("$[0].targetKey").value(blockId));

        createBinding(admin.token(), documentId, """
                {
                  "repositoryId":"%s",
                  "blockId":"%s",
                  "pathPattern":"%s",
                  "revision":"A",
                  "anchorKind":"RANGE",
                  "startLine":10,
                  "endLine":20
                }
                """.formatted(repositoryId, blockId, path))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GIT_BINDING_INVALID"));
    }

    @Test
    void shouldRejectStructurallyInvalidPreciseAnchors() throws Exception {
        AuthSession admin = register();
        String workspaceId = createWorkspace(admin.token());
        String documentId = createDocument(admin.token(), workspaceId);
        String repositoryId = registerRepository(admin.token(), workspaceId);
        String prefix = """
                {"repositoryId":"%s","pathPattern":"src/App.java",
                """.formatted(repositoryId);
        String[] invalidTails = {
                "\"anchorKind\":\"FILE\",\"symbolKey\":\"x\"}",
                "\"anchorKind\":\"FILE\",\"startLine\":1,\"endLine\":2}",
                "\"anchorKind\":\"RANGE\",\"startLine\":1,\"endLine\":2}",
                "\"revision\":\"A\",\"anchorKind\":\"RANGE\"}",
                "\"revision\":\"A\",\"anchorKind\":\"RANGE\",\"startLine\":1}",
                "\"revision\":\"A\",\"anchorKind\":\"RANGE\",\"startLine\":0,\"endLine\":2}",
                "\"revision\":\"A\",\"anchorKind\":\"RANGE\",\"startLine\":3,\"endLine\":2}",
                "\"revision\":\"A\",\"anchorKind\":\"RANGE\",\"symbolKey\":\"x\",\"startLine\":1,\"endLine\":2}",
                "\"anchorKind\":\"SYMBOL\",\"symbolKey\":\"x\"}",
                "\"revision\":\"A\",\"anchorKind\":\"SYMBOL\"}",
                "\"revision\":\"A\",\"anchorKind\":\"SYMBOL\",\"symbolKey\":\"x\",\"startLine\":1}",
                "\"revision\":\"A\",\"anchorKind\":\"SYMBOL\",\"symbolKey\":\"x\",\"startLine\":0,\"endLine\":1}",
                "\"revision\":\"A\",\"anchorKind\":\"SYMBOL\",\"symbolKey\":\"x\",\"startLine\":2,\"endLine\":1}"
        };

        for (String invalidTail : invalidTails) {
            createBinding(admin.token(), documentId, prefix + invalidTail)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GIT_BINDING_INVALID"));
        }
    }

    @Test
    void databaseConstraintsProtectLegacyDefaultsAndPreciseIdentity() throws Exception {
        AuthSession admin = register();
        String workspaceId = createWorkspace(admin.token());
        String documentId = createDocument(admin.token(), workspaceId);
        String repositoryId = registerRepository(admin.token(), workspaceId);
        UUID createdBy = jdbcTemplate.queryForObject(
                "SELECT id FROM user_accounts WHERE username = ?",
                UUID.class,
                admin.username()
        );
        UUID legacyId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO code_document_bindings
                    (id, workspace_id, repository_id, document_id, block_id,
                     target_key, path_pattern, created_by, created_at)
                VALUES (?, ?, ?, ?, NULL, 'DOCUMENT', 'src/Legacy.java', ?, ?)
                """,
                legacyId,
                UUID.fromString(workspaceId),
                UUID.fromString(repositoryId),
                UUID.fromString(documentId),
                createdBy,
                Instant.now()
        );

        var legacy = jdbcTemplate.queryForMap(
                "SELECT revision, anchor_kind, symbol_key, start_line, end_line "
                        + "FROM code_document_bindings WHERE id = ?",
                legacyId
        );
        assertThat(legacy.get("revision")).isNull();
        assertThat(legacy.get("anchor_kind")).isEqualTo("FILE");
        assertThat(legacy.get("symbol_key")).isNull();
        assertThat(legacy.get("start_line")).isNull();
        assertThat(legacy.get("end_line")).isNull();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO code_document_bindings
                    (id, workspace_id, repository_id, document_id, block_id,
                     target_key, path_pattern, revision, anchor_kind,
                     start_line, end_line, revision_key, start_line_key,
                     end_line_key, created_by, created_at)
                VALUES (?, ?, ?, ?, NULL, 'DOCUMENT', 'src/Invalid.java',
                        'A', 'FILE', 1, 2, 'A', 1, 2, ?, ?)
                """,
                UUID.randomUUID(),
                UUID.fromString(workspaceId),
                UUID.fromString(repositoryId),
                UUID.fromString(documentId),
                createdBy,
                Instant.now()
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO code_document_bindings
                    (id, workspace_id, repository_id, document_id, block_id,
                     target_key, path_pattern, created_by, created_at)
                VALUES (?, ?, ?, ?, NULL, 'DOCUMENT', 'src/Legacy.java', ?, ?)
                """,
                UUID.randomUUID(),
                UUID.fromString(workspaceId),
                UUID.fromString(repositoryId),
                UUID.fromString(documentId),
                createdBy,
                Instant.now()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldQueueGithubSyncExposeFilesAndQueueDeletion() throws Exception {
        AuthSession admin = register();
        String workspaceId = createWorkspace(admin.token());
        MvcResult created = mockMvc.perform(post(
                        "/api/v1/workspaces/{id}/git/repositories", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Hello World",
                                  "provider":"GITHUB",
                                  "remoteUrl":"https://github.com/octocat/Hello-World.git",
                                  "defaultBranch":"master"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.syncStatus").value("SYNC_PENDING"))
                .andReturn();
        String repositoryId = responseJson(created).get("id").asText();

        jdbcTemplate.update("""
                INSERT INTO git_repository_files
                    (id, repository_id, path, blob_sha, size_bytes, language)
                VALUES (?, ?, 'README', '0123456789abcdef', 42, 'Markdown')
                """, UUID.randomUUID(), UUID.fromString(repositoryId));

        mockMvc.perform(get(
                        "/api/v1/workspaces/{workspaceId}/git/repositories/{repositoryId}/files",
                        workspaceId, repositoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("README"));

        mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/git/repositories/{repositoryId}/sync",
                        workspaceId, repositoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.syncStatus").value("SYNC_PENDING"));

        mockMvc.perform(delete(
                        "/api/v1/workspaces/{workspaceId}/git/repositories/{repositoryId}",
                        workspaceId, repositoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isNoContent());

        Integer syncEvents = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM outbox_events
                 WHERE aggregate_id = ?
                   AND event_type = 'GIT_REPOSITORY_SYNC_REQUESTED'
                """, Integer.class, UUID.fromString(repositoryId));
        Integer deleteEvents = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM outbox_events
                 WHERE aggregate_id = ?
                   AND event_type = 'GIT_REPOSITORY_DELETE_REQUESTED'
                """, Integer.class, UUID.fromString(repositoryId));
        assertThat(syncEvents).isEqualTo(2);
        assertThat(deleteEvents).isEqualTo(1);
    }

    private JsonNode ingestChange(String token, String workspaceId, String repositoryId)
            throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/workspaces/{workspaceId}/git/repositories/{repositoryId}/changes",
                        workspaceId,
                        repositoryId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.authorEmail").value("author@example.com"))
                .andExpect(jsonPath("$.committerName").value("DevCollab Bot"))
                .andExpect(jsonPath("$.parentCommitSha").value("abcdef1234567"))
                .andExpect(jsonPath("$.files[0].changeType").value("MODIFIED"))
                .andExpect(jsonPath("$.files[0].binaryFile").value(false))
                .andReturn();
        return responseJson(result);
    }

    private ResultActions createBinding(
            String token,
            String documentId,
            String body
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/documents/{id}/code-bindings", documentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String changeBody() {
        return """
                {
                  "changeType":"COMMIT",
                  "externalId":"commit-001",
                  "title":"Update application service",
                  "commitSha":"0123456789abcdef0123456789abcdef01234567",
                  "headRef":"main",
                  "authorName":"DevCollab Test",
                  "authorEmail":"author@example.com",
                  "authoredAt":"2026-07-18T23:59:00Z",
                  "committerName":"DevCollab Bot",
                  "committerEmail":"bot@example.com",
                  "parentCommitSha":"abcdef1234567",
                  "webUrl":"https://example.com/devcollab/commit/001",
                  "occurredAt":"%s",
                  "files":[{
                    "path":"knowledge-core/src/main/java/App.java",
                    "changeType":"MODIFIED",
                    "additions":8,
                    "deletions":2,
                    "binaryFile":false,
                    "patchExcerpt":"@@ application service @@"
                  }]
                }
                """.formatted(Instant.parse("2026-07-19T00:00:00Z"));
    }

    private String registerRepository(String token, String workspaceId) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/workspaces/{id}/git/repositories", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(repositoryBody("https://example.com/devcollab/core.git")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("GENERIC"))
                .andReturn();
        return responseJson(result).get("id").asText();
    }

    private String repositoryBody(String remoteUrl) {
        return """
                {
                  "name":"DevCollab Core",
                  "provider":"GENERIC",
                  "remoteUrl":"%s",
                  "defaultBranch":"main"
                }
                """.formatted(remoteUrl);
    }

    private String createWorkspace(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Git Knowledge Workspace\"}"))
                .andExpect(status().isCreated()).andReturn();
        return responseJson(result).get("id").asText();
    }

    private String createDocument(String token, String workspaceId) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/workspaces/{id}/documents", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Git Linked Document\",\"documentType\":\"REQUIREMENT\"}"))
                .andExpect(status().isCreated()).andReturn();
        return responseJson(result).get("id").asText();
    }

    private String createBlock(String token, String documentId) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/documents/{id}/blocks", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PARAGRAPH\",\"content\":{\"text\":\"linked\"}}"))
                .andExpect(status().isCreated()).andReturn();
        return responseJson(result).get("id").asText();
    }

    private void inviteMember(String token, String workspaceId, String username)
            throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{id}/members/invitations", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"role\":\"MEMBER\"}"
                                .formatted(username)))
                .andExpect(status().isCreated());
    }

    private AuthSession register() throws Exception {
        String username = "git_" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"Git Test","password":"password123"}
                                """.formatted(username)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode response = responseJson(result);
        return new AuthSession(username, response.get("accessToken").asText());
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthSession(String username, String token) {
    }
}
