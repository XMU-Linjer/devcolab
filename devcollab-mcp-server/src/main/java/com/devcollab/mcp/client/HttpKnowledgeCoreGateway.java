package com.devcollab.mcp.client;

import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import com.devcollab.mcp.security.McpUserIdentity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.UUID;

@Component
public class HttpKnowledgeCoreGateway implements KnowledgeCoreGateway {

    private final RestClient restClient;

    public HttpKnowledgeCoreGateway(RestClient knowledgeCoreRestClient) {
        this.restClient = knowledgeCoreRestClient;
    }

    @Override
    public WorkspaceContext getWorkspaceContext(UUID workspaceId, McpUserIdentity identity) {
        try {
            WorkspacePayload workspace = get(
                    "/api/v1/workspaces/{workspaceId}",
                    identity,
                    WorkspacePayload.class,
                    workspaceId
            );
            List<RepositoryPayload> repositories = restClient.get()
                    .uri("/api/v1/workspaces/{workspaceId}/git/repositories", workspaceId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(identity))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return new WorkspaceContext(
                    workspace.id(),
                    workspace.name(),
                    workspace.currentUserRole(),
                    repositories == null ? List.of() : repositories.stream()
                            .map(repository -> new RepositoryContext(
                                    repository.id(),
                                    repository.name(),
                                    repository.provider(),
                                    repository.remoteUrl(),
                                    repository.defaultBranch(),
                                    repository.syncStatus(),
                                    repository.lastSyncedCommit()
                            ))
                            .toList()
            );
        } catch (RuntimeException exception) {
            throw map(exception, McpToolErrorCode.WORKSPACE_NOT_FOUND);
        }
    }

    @Override
    public RepositorySource readRepositorySource(
            UUID workspaceId,
            UUID repositoryId,
            String path,
            McpUserIdentity identity
    ) {
        try {
            SourcePayload source = restClient.get()
                    .uri(builder -> builder
                            .path("/api/v1/workspaces/{workspaceId}/git/repositories/{repositoryId}/source")
                            .queryParam("path", path)
                            .build(workspaceId, repositoryId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(identity))
                    .retrieve()
                    .body(SourcePayload.class);
            if (source == null) {
                throw new McpToolException(McpToolErrorCode.FILE_NOT_FOUND, "Repository file was not found");
            }
            return new RepositorySource(
                    source.repositoryId(),
                    source.commitSha(),
                    source.path(),
                    source.sizeBytes(),
                    source.language(),
                    source.readable(),
                    source.content()
            );
        } catch (RuntimeException exception) {
            throw map(exception, McpToolErrorCode.FILE_NOT_FOUND);
        }
    }

    private <T> T get(
            String path,
            McpUserIdentity identity,
            Class<T> responseType,
            Object... pathVariables
    ) {
        T response = restClient.get()
                .uri(path, pathVariables)
                .header(HttpHeaders.AUTHORIZATION, bearer(identity))
                .retrieve()
                .body(responseType);
        if (response == null) {
            throw new McpToolException(McpToolErrorCode.INTERNAL_ERROR, "Knowledge Core returned an empty response");
        }
        return response;
    }

    private RuntimeException map(RuntimeException exception, McpToolErrorCode notFoundFallback) {
        if (exception instanceof McpToolException toolException) {
            return toolException;
        }
        if (exception instanceof ResourceAccessException) {
            return new McpToolException(McpToolErrorCode.CORE_UNAVAILABLE, "Knowledge Core is unavailable");
        }
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            if (status.value() == 401 || status.value() == 403) {
                return new McpToolException(McpToolErrorCode.PERMISSION_DENIED, "Workspace access was denied");
            }
            if (status.value() == 404) {
                String code = extractErrorCode(responseException.getResponseBodyAsString());
                if ("GIT_REPOSITORY_NOT_FOUND".equals(code)) {
                    return new McpToolException(McpToolErrorCode.REPOSITORY_NOT_FOUND, "Repository was not found");
                }
                if ("GIT_REPOSITORY_FILE_NOT_FOUND".equals(code)) {
                    return new McpToolException(McpToolErrorCode.FILE_NOT_FOUND, "Repository file was not found");
                }
                return new McpToolException(notFoundFallback, "Requested DevCollab context was not found");
            }
            if (status.is5xxServerError()) {
                return new McpToolException(McpToolErrorCode.CORE_UNAVAILABLE, "Knowledge Core is unavailable");
            }
        }
        return new McpToolException(McpToolErrorCode.INTERNAL_ERROR, "Knowledge Core request failed");
    }

    private String extractErrorCode(String body) {
        int marker = body == null ? -1 : body.indexOf("\"code\"");
        if (marker < 0) {
            return "";
        }
        int separator = body.indexOf(':', marker);
        int start = body.indexOf('"', separator + 1);
        int end = start < 0 ? -1 : body.indexOf('"', start + 1);
        return start >= 0 && end > start ? body.substring(start + 1, end) : "";
    }

    private String bearer(McpUserIdentity identity) {
        return "Bearer " + identity.accessToken();
    }

    private record WorkspacePayload(UUID id, String name, String currentUserRole) {
    }

    private record RepositoryPayload(
            UUID id,
            String name,
            String provider,
            String remoteUrl,
            String defaultBranch,
            String syncStatus,
            String lastSyncedCommit
    ) {
    }

    private record SourcePayload(
            UUID repositoryId,
            String commitSha,
            String path,
            long sizeBytes,
            String language,
            boolean readable,
            String content
    ) {
    }
}
