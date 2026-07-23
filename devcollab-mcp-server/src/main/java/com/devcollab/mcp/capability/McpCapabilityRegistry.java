package com.devcollab.mcp.capability;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class McpCapabilityRegistry {

    private final List<McpToolContributor> toolContributors;
    private final List<McpResourceContributor> resourceContributors;

    public McpCapabilityRegistry(
            List<McpToolContributor> toolContributors,
            List<McpResourceContributor> resourceContributors
    ) {
        this.toolContributors = List.copyOf(toolContributors);
        this.resourceContributors = List.copyOf(resourceContributors);
    }

    public List<McpServerFeatures.SyncToolSpecification> tools() {
        return toolContributors.stream()
                .flatMap(contributor -> contributor.tools().stream())
                .toList();
    }

    public List<McpServerFeatures.SyncResourceSpecification> resources() {
        return resourceContributors.stream()
                .flatMap(contributor -> contributor.resources().stream())
                .toList();
    }
}
