package com.devcollab.knowledgecore.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryPathValidatorTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "src/Main.java",
            "src/main/java/App.java",
            "report..md",
            "dir/file..txt",
            ".github/workflows/test.yml",
            "a.b/c..d.txt"
    })
    void validPathsPass(String path) {
        assertThatCode(() -> RepositoryPathValidator.validate(path, "error")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../a",
            "a/../b",
            "..\\a",
            "a\\..\\b",
            "/etc/passwd",
            "\\absolute",
            "C:\\file",
            "C:/file",
            "C:file",
            "\\\\server\\share"
    })
    void invalidPathsFail(String path) {
        assertThatThrownBy(() -> RepositoryPathValidator.validate(path, "bad path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad path");
    }

    @Test
    void nullPathFails() {
        assertThatThrownBy(() -> RepositoryPathValidator.validate(null, "null"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyStringFails() {
        assertThatThrownBy(() -> RepositoryPathValidator.validate("", "empty"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whitespaceOnlyFails() {
        assertThatThrownBy(() -> RepositoryPathValidator.validate("   ", "spaces"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nulCharacterFails() {
        assertThatThrownBy(() -> RepositoryPathValidator.validate("file\0.txt", "nul"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeReplacesBackslash() {
        assertThat(RepositoryPathValidator.normalize("src\\main\\java"))
                .isEqualTo("src/main/java");
    }

    @Test
    void normalizeRemovesCurrentDirectorySegments() {
        assertThat(RepositoryPathValidator.normalize("./src/./main/App.java"))
                .isEqualTo("src/main/App.java");
    }

    @Test
    void currentDirectoryOnlyFails() {
        assertThatThrownBy(() -> RepositoryPathValidator.validate("./.", "bad path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad path");
    }
}
