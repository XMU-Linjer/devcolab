package com.devcollab.mcp.application;

import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;

final class RepositoryPathPolicy {

    private RepositoryPathPolicy() {
    }

    static String normalize(String path, boolean allowRoot) {
        if (path == null || path.isBlank()) {
            if (allowRoot) {
                return "";
            }
            throw new McpToolException(
                    McpToolErrorCode.INVALID_ARGUMENT,
                    "Repository path is required"
            );
        }
        String normalized = path.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("//")
                || normalized.matches("^[a-zA-Z]:.*") || normalized.contains("\0")) {
            throw invalid();
        }
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || "..".equals(segment)) {
                throw invalid();
            }
        }
        String result = String.join(
                "/",
                java.util.Arrays.stream(segments)
                        .filter(segment -> !".".equals(segment))
                        .toList()
        );
        if (result.isEmpty() && !allowRoot) {
            throw invalid();
        }
        return result;
    }

    private static McpToolException invalid() {
        return new McpToolException(
                McpToolErrorCode.INVALID_REPOSITORY_PATH,
                "Repository path must stay inside the registered repository"
        );
    }
}
