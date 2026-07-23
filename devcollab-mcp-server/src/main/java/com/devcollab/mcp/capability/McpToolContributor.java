package com.devcollab.mcp.capability;

import io.modelcontextprotocol.server.McpServerFeatures;

import java.util.List;

public interface McpToolContributor {

    List<McpServerFeatures.SyncToolSpecification> tools();
}
