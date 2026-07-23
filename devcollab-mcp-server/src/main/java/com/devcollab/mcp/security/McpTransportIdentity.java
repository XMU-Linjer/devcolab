package com.devcollab.mcp.security;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;

public final class McpTransportIdentity {

    public static final String CONTEXT_KEY = "devcollab.userIdentity";

    private McpTransportIdentity() {
    }

    public static McpUserIdentity require(McpSyncServerExchange exchange) {
        Object identity = exchange.transportContext().get(CONTEXT_KEY);
        if (identity instanceof McpUserIdentity userIdentity) {
            return userIdentity;
        }
        throw new IllegalStateException("Authenticated MCP identity is missing from transport context");
    }

    public static McpTransportContext context(McpUserIdentity identity) {
        return McpTransportContext.create(java.util.Map.of(CONTEXT_KEY, identity));
    }
}
