package com.devcollab.knowledgecore.common.util;

public final class RepositoryPathValidator {

    private RepositoryPathValidator() {
    }

    public static void validate(String path, String errorMessage) {
        if (path == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        if (path.contains("\0")) {
            throw new IllegalArgumentException(errorMessage);
        }
        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        String normalized = trimmed.replace('\\', '/');
        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException(errorMessage);
        }
        if (normalized.matches("^[a-zA-Z]:.*")) {
            throw new IllegalArgumentException(errorMessage);
        }
        if (normalized.startsWith("//")) {
            throw new IllegalArgumentException(errorMessage);
        }
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException(errorMessage);
            }
            if (segment.isEmpty()) {
                throw new IllegalArgumentException(errorMessage);
            }
        }
        if (normalize(normalized).isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public static String normalize(String path) {
        String trimmed = path.trim();
        String normalized = trimmed.replace('\\', '/');
        return String.join(
                "/",
                java.util.Arrays.stream(normalized.split("/"))
                        .filter(segment -> !".".equals(segment))
                        .toList()
        );
    }
}
