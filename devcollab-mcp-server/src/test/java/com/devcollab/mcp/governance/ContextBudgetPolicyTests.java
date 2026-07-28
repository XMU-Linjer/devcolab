package com.devcollab.mcp.governance;

import com.devcollab.mcp.config.McpProperties;
import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextBudgetPolicyTests {

    @Test
    void rejectsAbsoluteAndTraversalPaths() {
        ContextBudgetPolicy policy = policy(400, 40_000, 2_048);

        assertThatThrownBy(() -> policy.validateRepositoryPath("C:\\secret.txt"))
                .isInstanceOfSatisfying(McpToolException.class,
                        error -> assertThat(error.code()).isEqualTo(McpToolErrorCode.INVALID_REPOSITORY_PATH));
        assertThatThrownBy(() -> policy.validateRepositoryPath("/etc/passwd"))
                .isInstanceOf(McpToolException.class);
        assertThatThrownBy(() -> policy.validateRepositoryPath("src/../secret.txt"))
                .isInstanceOf(McpToolException.class);
    }

    @Test
    void rejectsHalfAndReversedLineRanges() {
        ContextBudgetPolicy policy = policy(400, 40_000, 2_048);

        assertThatThrownBy(() -> policy.limit("one\ntwo", 1, null))
                .isInstanceOfSatisfying(McpToolException.class,
                        error -> assertThat(error.code()).isEqualTo(McpToolErrorCode.INVALID_LINE_RANGE));
        assertThatThrownBy(() -> policy.limit("one\ntwo", 2, 1))
                .isInstanceOf(McpToolException.class);
    }

    @Test
    void lineAndCharacterBudgetsReportRealOmissions() {
        ContextBudgetPolicy linePolicy = policy(2, 40_000, 2_048);
        ContextBudgetPolicy.BudgetedContent byLines =
                linePolicy.limit("one\ntwo\nthree\nfour", null, null);
        assertThat(byLines.content()).isEqualTo("one\ntwo");
        assertThat(byLines.truncated()).isTrue();
        assertThat(byLines.omittedLineCount()).isEqualTo(2);
        assertThat(byLines.omittedCharacterCount()).isGreaterThan(0);

        ContextBudgetPolicy characterPolicy = policy(400, 5, 2_048);
        ContextBudgetPolicy.BudgetedContent byCharacters =
                characterPolicy.limit("abcdef\nsecond", null, null);
        assertThat(byCharacters.content()).isEqualTo("abcde");
        assertThat(byCharacters.truncated()).isTrue();
        assertThat(byCharacters.omittedCharacterCount()).isEqualTo(8);
    }

    private ContextBudgetPolicy policy(int lines, int characters, int pathCharacters) {
        return new ContextBudgetPolicy(new McpProperties(
                "/mcp",
                "test",
                "1",
                lines,
                characters,
                pathCharacters,
                100,
                30000,
                50,
                20,
                500,
                200,
                100,
                List.of("http://localhost:*"),
                List.of("localhost:*"),
                URI.create("http://localhost:8080"),
                Duration.ofSeconds(1)
        ));
    }
}
