package com.devcollab.knowledgecore.search.api;

import com.devcollab.knowledgecore.search.domain.SearchHighlightRange;
import com.devcollab.knowledgecore.search.domain.SearchSnippetHighlighter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchSnippetHighlighterTests {

    @Test
    void shouldReturnHighlightRangeForMatchedKeyword() {
        SearchSnippetHighlighter.Result result =
                SearchSnippetHighlighter.create(
                        "The order API needs idempotency key",
                        "api"
                );

        assertThat(result.snippet())
                .isEqualTo("The order API needs idempotency key");
        assertThat(result.highlights())
                .containsExactly(new SearchHighlightRange(10, 13));
    }

    @Test
    void shouldKeepHighlightRangeInsideTrimmedSnippet() {
        SearchSnippetHighlighter.Result result =
                SearchSnippetHighlighter.create(
                        "prefix ".repeat(20)
                                + "POST /api/orders requires idempotency key",
                        "idempotency"
                );

        assertThat(result.snippet()).startsWith("...");
        assertThat(result.snippet()).contains("idempotency");
        assertThat(result.highlights()).hasSize(1);

        SearchHighlightRange range = result.highlights().getFirst();
        assertThat(result.snippet().substring(range.start(), range.end()))
                .isEqualTo("idempotency");
    }
}
