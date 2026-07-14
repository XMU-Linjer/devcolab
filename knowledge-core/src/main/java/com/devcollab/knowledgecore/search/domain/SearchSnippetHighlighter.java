package com.devcollab.knowledgecore.search.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SearchSnippetHighlighter {

    private static final int SNIPPET_LIMIT = 120;
    private static final int CONTEXT_BEFORE_HIT = 40;

    private SearchSnippetHighlighter() {
    }

    public static Result create(String text, String keyword) {
        String normalizedText = text == null ? "" : text;
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedText.isBlank() || normalizedKeyword.isBlank()) {
            return new Result(normalizedText, List.of());
        }

        String lowerText = normalizedText.toLowerCase(Locale.ROOT);
        String lowerKeyword = normalizedKeyword.toLowerCase(Locale.ROOT);
        int hitIndex = lowerText.indexOf(lowerKeyword);

        if (normalizedText.length() <= SNIPPET_LIMIT) {
            return new Result(
                    normalizedText,
                    rangesInWindow(
                            lowerText,
                            lowerKeyword,
                            0,
                            normalizedText.length(),
                            0
                    )
            );
        }

        if (hitIndex < 0) {
            return new Result(
                    normalizedText.substring(0, SNIPPET_LIMIT) + "...",
                    List.of()
            );
        }

        int start = Math.max(0, hitIndex - CONTEXT_BEFORE_HIT);
        int end = Math.min(normalizedText.length(), start + SNIPPET_LIMIT);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < normalizedText.length() ? "..." : "";
        String snippet = prefix + normalizedText.substring(start, end) + suffix;
        int snippetOffset = prefix.length() - start;

        return new Result(
                snippet,
                rangesInWindow(
                        lowerText,
                        lowerKeyword,
                        start,
                        end,
                        snippetOffset
                )
        );
    }

    private static List<SearchHighlightRange> rangesInWindow(
            String lowerText,
            String lowerKeyword,
            int windowStart,
            int windowEnd,
            int snippetOffset
    ) {
        List<SearchHighlightRange> ranges = new ArrayList<>();
        int fromIndex = windowStart;
        while (fromIndex < windowEnd) {
            int index = lowerText.indexOf(lowerKeyword, fromIndex);
            if (index < 0 || index >= windowEnd) {
                break;
            }

            int end = Math.min(index + lowerKeyword.length(), windowEnd);
            ranges.add(new SearchHighlightRange(
                    index + snippetOffset,
                    end + snippetOffset
            ));
            fromIndex = index + lowerKeyword.length();
        }
        return List.copyOf(ranges);
    }

    public record Result(
            String snippet,
            List<SearchHighlightRange> highlights
    ) {
    }
}
