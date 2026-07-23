package com.devcollab.mcp.capability;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpCapabilityRegistryTests {

    @Test
    void emptyContributorSetProducesEmptyCapabilities() {
        McpCapabilityRegistry registry = new McpCapabilityRegistry(List.of(), List.of());

        assertThat(registry.tools()).isEmpty();
        assertThat(registry.resources()).isEmpty();
    }

    @Test
    void registryDiscoversContributorsWithoutTransportChanges() {
        McpToolContributor contributor = () -> List.of(
                new McpServerFeatures.SyncToolSpecification(null, (exchange, request) -> null)
        );
        McpCapabilityRegistry registry = new McpCapabilityRegistry(List.of(contributor), List.of());

        assertThat(registry.tools()).hasSize(1);
    }
}
