package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.git.application.exception.InvalidCodeBindingException;
import com.devcollab.knowledgecore.git.domain.CodeAnchorKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeBindingAnchorValidatorTests {

    @Test
    void acceptsLegacyAndRevisionAwareFileAnchors() {
        var legacy = CodeBindingAnchorValidator.validate(
                null, null, null, null, null
        );
        var revisionAware = CodeBindingAnchorValidator.validate(
                "  abc123  ", CodeAnchorKind.FILE, null, null, null
        );

        assertThat(legacy.anchorKind()).isEqualTo(CodeAnchorKind.FILE);
        assertThat(legacy.revision()).isNull();
        assertThat(revisionAware.revision()).isEqualTo("abc123");
    }

    @Test
    void acceptsRangeAndSymbolAnchors() {
        var range = CodeBindingAnchorValidator.validate(
                "abc123", CodeAnchorKind.RANGE, null, 10, 20
        );
        var symbol = CodeBindingAnchorValidator.validate(
                "abc123", CodeAnchorKind.SYMBOL, " java:demo.Order ", 10, 20
        );
        var symbolWithoutRange = CodeBindingAnchorValidator.validate(
                "abc123", CodeAnchorKind.SYMBOL, "java:demo.Order", null, null
        );

        assertThat(range.startLine()).isEqualTo(10);
        assertThat(symbol.symbolKey()).isEqualTo("java:demo.Order");
        assertThat(symbolWithoutRange.startLine()).isNull();
    }

    @Test
    void rejectsIllegalFileAnchors() {
        assertInvalid(CodeAnchorKind.FILE, null, "symbol", null, null);
        assertInvalid(CodeAnchorKind.FILE, null, null, 1, 2);
    }

    @Test
    void rejectsIllegalRangeAnchors() {
        assertInvalid(CodeAnchorKind.RANGE, null, null, 1, 2);
        assertInvalid(CodeAnchorKind.RANGE, "rev", null, null, null);
        assertInvalid(CodeAnchorKind.RANGE, "rev", null, 1, null);
        assertInvalid(CodeAnchorKind.RANGE, "rev", null, 0, 2);
        assertInvalid(CodeAnchorKind.RANGE, "rev", null, 3, 2);
        assertInvalid(CodeAnchorKind.RANGE, "rev", "symbol", 1, 2);
    }

    @Test
    void rejectsIllegalSymbolAnchors() {
        assertInvalid(CodeAnchorKind.SYMBOL, null, "symbol", null, null);
        assertInvalid(CodeAnchorKind.SYMBOL, "rev", null, null, null);
        assertInvalid(CodeAnchorKind.SYMBOL, "rev", "symbol", 1, null);
        assertInvalid(CodeAnchorKind.SYMBOL, "rev", "symbol", 0, 1);
        assertInvalid(CodeAnchorKind.SYMBOL, "rev", "symbol", 2, 1);
    }

    @Test
    void rejectsBlankOptionalStrings() {
        assertInvalid(CodeAnchorKind.FILE, "  ", null, null, null);
        assertInvalid(CodeAnchorKind.SYMBOL, "rev", "  ", null, null);
    }

    private void assertInvalid(
            CodeAnchorKind kind,
            String revision,
            String symbolKey,
            Integer startLine,
            Integer endLine
    ) {
        assertThatThrownBy(() -> CodeBindingAnchorValidator.validate(
                revision, kind, symbolKey, startLine, endLine
        )).isInstanceOf(InvalidCodeBindingException.class);
    }
}
