package com.devcollab.mcp.governance;

import com.devcollab.mcp.config.McpProperties;
import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ContextBudgetPolicy {

    private final McpProperties properties;

    public ContextBudgetPolicy(McpProperties properties) {
        this.properties = properties;
    }

    public void validateRepositoryPath(String path) {
        if (path == null || path.isBlank()) {
            throw new McpToolException(McpToolErrorCode.INVALID_REPOSITORY_PATH, "Repository path is required");
        }
        if (path.length() > properties.maxPathCharacters()) {
            throw new McpToolException(
                    McpToolErrorCode.CONTEXT_LIMIT_EXCEEDED,
                    "Repository path exceeds the configured context limit"
            );
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:/.*")
                || List.of(normalized.split("/")).contains("..")) {
            throw new McpToolException(
                    McpToolErrorCode.INVALID_REPOSITORY_PATH,
                    "Repository path must be a safe relative path"
            );
        }
    }

    public BudgetedContent limit(String content, Integer requestedStartLine, Integer requestedEndLine) {
        String safeContent = content == null ? "" : content;
        String[] allLines = safeContent.split("\\R", -1);
        int totalLines = allLines.length;

        if ((requestedStartLine == null) != (requestedEndLine == null)) {
            throw new McpToolException(
                    McpToolErrorCode.INVALID_LINE_RANGE,
                    "startLine and endLine must be supplied together"
            );
        }

        int startLine = requestedStartLine == null ? 1 : requestedStartLine;
        int endLine = requestedEndLine == null
                ? Math.min(totalLines, properties.maxCodeLines())
                : requestedEndLine;
        if (startLine < 1 || endLine < startLine || startLine > totalLines) {
            throw new McpToolException(
                    McpToolErrorCode.INVALID_LINE_RANGE,
                    "Requested line range is outside the file"
            );
        }

        endLine = Math.min(endLine, totalLines);
        int budgetedEndLine = Math.min(endLine, startLine + properties.maxCodeLines() - 1);
        StringBuilder selected = new StringBuilder();
        int actualEndLine = startLine - 1;
        for (int line = startLine; line <= budgetedEndLine; line++) {
            String candidate = allLines[line - 1];
            int separatorLength = selected.isEmpty() ? 0 : 1;
            if (selected.length() + separatorLength + candidate.length() > properties.maxOutputCharacters()) {
                int remaining = properties.maxOutputCharacters() - selected.length() - separatorLength;
                if (remaining > 0) {
                    if (!selected.isEmpty()) {
                        selected.append('\n');
                    }
                    selected.append(candidate, 0, Math.min(candidate.length(), remaining));
                }
                break;
            }
            if (!selected.isEmpty()) {
                selected.append('\n');
            }
            selected.append(candidate);
            actualEndLine = line;
        }

        boolean truncated = actualEndLine < endLine
                || requestedEndLine == null && endLine < totalLines
                || selected.length() >= properties.maxOutputCharacters();
        int omittedLineCount = truncated ? Math.max(0, totalLines - Math.max(actualEndLine, 0)) : 0;
        return new BudgetedContent(
                selected.toString(),
                startLine,
                Math.max(actualEndLine, startLine),
                totalLines,
                truncated,
                omittedLineCount
        );
    }

    public record BudgetedContent(
            String content,
            int startLine,
            int endLine,
            int totalLines,
            boolean truncated,
            int omittedLineCount
    ) {
        public Map<String, Object> metadata() {
            return Map.of(
                    "startLine", startLine,
                    "endLine", endLine,
                    "totalLines", totalLines,
                    "truncated", truncated,
                    "omittedLineCount", omittedLineCount
            );
        }
    }
}
