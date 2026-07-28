package com.devcollab.knowledgecore.agentdelegation;

import com.devcollab.knowledgecore.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
public class AgentDelegationController {

    private final AgentDelegationService service;

    public AgentDelegationController(AgentDelegationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/agent-delegations")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateResponse create(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CreateRequest request
    ) {
        AgentDelegation created = service.create(
                request.jobId(), request.workspaceId(), request.repositoryId(),
                request.scopeType(), currentUser.userId()
        );
        return new CreateResponse(
                created.id(), created.createdByUserId(), created.revision(),
                created.expiresAt()
        );
    }

    @PostMapping("/api/v1/agent-delegations/{delegationId}/authorize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void authorize(
            @PathVariable UUID delegationId,
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody JobRequest request
    ) {
        service.authorize(delegationId, request.jobId(), currentUser.userId());
    }

    @PostMapping("/api/v1/internal/agent-delegations/{delegationId}/exchange")
    public ExchangeResponse exchange(
            @PathVariable UUID delegationId,
            @RequestHeader(name = "X-DevCollab-Service-Token", required = false)
            String serviceToken,
            @Valid @RequestBody JobRequest request
    ) {
        return new ExchangeResponse(
                service.exchange(delegationId, request.jobId(), serviceToken)
        );
    }

    public record CreateRequest(
            @NotNull UUID jobId,
            @NotNull UUID workspaceId,
            @NotNull UUID repositoryId,
            @NotNull String scopeType
    ) {
    }

    public record JobRequest(@NotNull UUID jobId) {
    }

    public record CreateResponse(
            UUID delegationId,
            UUID createdByUserId,
            String revision,
            Instant expiresAt
    ) {
    }

    public record ExchangeResponse(String accessToken) {
    }
}
