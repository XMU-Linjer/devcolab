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
        int requestedFinalLine = requestedEndLine == null ? totalLines : requestedEndLine;
        if (startLine < 1 || requestedFinalLine < startLine || startLine > totalLines) {
            throw new McpToolException(
                    McpToolErrorCode.INVALID_LINE_RANGE,
                    "Requested line range is outside the file"
            );
        }

        requestedFinalLine = Math.min(requestedFinalLine, totalLines);
        int budgetedEndLine = Math.min(
                requestedFinalLine,
                startLine + properties.maxCodeLines() - 1
        );
        String requestedContent = join(allLines, startLine, requestedFinalLine);
        String lineBudgetedContent = join(allLines, startLine, budgetedEndLine);
        String selected = lineBudgetedContent.length() <= properties.maxOutputCharacters()
                ? lineBudgetedContent
                : lineBudgetedContent.substring(0, properties.maxOutputCharacters());

        int selectedLineBreaks = Math.toIntExact(selected.chars().filter(character -> character == '\n').count());
        int representedLineOffset = selected.endsWith("\n")
                ? Math.max(0, selectedLineBreaks - 1)
                : selectedLineBreaks;
        int actualEndLine = selected.isEmpty()
                ? startLine
                : Math.min(budgetedEndLine, startLine + representedLineOffset);
        boolean truncated = selected.length() < requestedContent.length();
        int omittedLineCount = truncated ? Math.max(0, requestedFinalLine - actualEndLine) : 0;
        int omittedCharacterCount = truncated
                ? Math.max(0, requestedContent.length() - selected.length())
                : 0;
        return new BudgetedContent(
                selected,
                startLine,
                actualEndLine,
                totalLines,
                truncated,
                omittedLineCount,
                omittedCharacterCount
        );
    }

    private String join(String[] lines, int startLine, int endLine) {
        StringBuilder joined = new StringBuilder();
        for (int line = startLine; line <= endLine; line++) {
            if (!joined.isEmpty()) {
                joined.append('\n');
            }
            joined.append(lines[line - 1]);
        }
        return joined.toString();
    }

    public record BudgetedContent(
            String content,
            int startLine,
            int endLine,
            int totalLines,
            boolean truncated,
            int omittedLineCount,
            int omittedCharacterCount
    ) {
        public Map<String, Object> metadata() {
            return Map.of(
                    "startLine", startLine,
                    "endLine", endLine,
                    "totalLines", totalLines,
                    "truncated", truncated,
                    "omittedLineCount", omittedLineCount,
                    "omittedCharacterCount", omittedCharacterCount
            );
        }
    }
}
