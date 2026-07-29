package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.git.application.exception.InvalidCodeBindingException;
import com.devcollab.knowledgecore.git.domain.CodeAnchorKind;

public final class CodeBindingAnchorValidator {

    private CodeBindingAnchorValidator() {
    }

    public static ValidatedAnchor validate(
            String revision,
            CodeAnchorKind anchorKind,
            String symbolKey,
            Integer startLine,
            Integer endLine
    ) {
        CodeAnchorKind normalizedKind = anchorKind == null ? CodeAnchorKind.FILE : anchorKind;
        String normalizedRevision = trimToNull(revision);
        String normalizedSymbolKey = trimToNull(symbolKey);

        if (revision != null && normalizedRevision == null) {
            throw invalid("revision 不能为空白字符串");
        }
        if (symbolKey != null && normalizedSymbolKey == null) {
            throw invalid("symbolKey 不能为空白字符串");
        }
        if ((startLine == null) != (endLine == null)) {
            throw invalid("startLine 和 endLine 必须同时存在或同时为空");
        }
        if (startLine != null && (startLine < 1 || endLine < startLine)) {
            throw invalid("代码行范围必须满足 startLine >= 1 且 endLine >= startLine");
        }

        switch (normalizedKind) {
            case FILE -> {
                if (normalizedSymbolKey != null || startLine != null) {
                    throw invalid("FILE 锚点不能包含 symbolKey 或代码行范围");
                }
            }
            case RANGE -> {
                if (normalizedRevision == null || startLine == null) {
                    throw invalid("RANGE 锚点必须包含 revision 和完整代码行范围");
                }
                if (normalizedSymbolKey != null) {
                    throw invalid("RANGE 锚点不能包含 symbolKey");
                }
            }
            case SYMBOL -> {
                if (normalizedRevision == null || normalizedSymbolKey == null) {
                    throw invalid("SYMBOL 锚点必须包含 revision 和 symbolKey");
                }
            }
        }

        return new ValidatedAnchor(
                normalizedRevision,
                normalizedKind,
                normalizedSymbolKey,
                startLine,
                endLine
        );
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static InvalidCodeBindingException invalid(String message) {
        return new InvalidCodeBindingException(message);
    }

    public record ValidatedAnchor(
            String revision,
            CodeAnchorKind anchorKind,
            String symbolKey,
            Integer startLine,
            Integer endLine
    ) {
    }
}
