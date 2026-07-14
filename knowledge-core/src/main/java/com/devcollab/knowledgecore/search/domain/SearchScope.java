package com.devcollab.knowledgecore.search.domain;

import java.util.Locale;

public enum SearchScope {
    ALL,
    TITLE,
    CONTENT;

    public static SearchScope from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }

        try {
            return SearchScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ALL;
        }
    }

    public boolean includesTitle() {
        return this == ALL || this == TITLE;
    }

    public boolean includesContent() {
        return this == ALL || this == CONTENT;
    }
}
