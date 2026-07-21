package com.devcollab.worker.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JavaCodeGraphAnalyzerTests {

    Path repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = Path.of("target", "code-graph-tests", UUID.randomUUID().toString())
                .toAbsolutePath().normalize();
        Files.createDirectories(repository);
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (!Files.exists(repository)) return;
        try (var paths = Files.walk(repository)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void projectsStableSymbolsAndInternalDependencies() throws Exception {
        write("src/main/java/demo/api/OrderPort.java", """
                package demo.api;
                public interface OrderPort {
                    String find(String id);
                }
                """);
        write("src/main/java/demo/service/OrderService.java", """
                package demo.service;
                import demo.api.OrderPort;
                public class OrderService implements OrderPort {
                    private final String prefix = "order";
                    public OrderService() {}
                    public String find(String id) { return prefix + id; }
                }
                """);

        CodeGraphProjection graph = new JavaCodeGraphAnalyzer().analyze(
                repository,
                List.of(
                        file("src/main/java/demo/api/OrderPort.java"),
                        file("src/main/java/demo/service/OrderService.java")
                )
        );

        assertThat(graph.parseFailures()).isEmpty();
        assertThat(graph.symbols())
                .extracting(CodeSymbolProjection::qualifiedName)
                .contains(
                        "demo.api.OrderPort",
                        "demo.api.OrderPort#find",
                        "demo.service.OrderService",
                        "demo.service.OrderService#<init>",
                        "demo.service.OrderService#find",
                        "demo.service.OrderService#prefix"
                );
        String portKey = JavaCodeGraphAnalyzer.typeSymbolKey(
                "src/main/java/demo/api/OrderPort.java", "demo.api.OrderPort"
        );
        String serviceKey = JavaCodeGraphAnalyzer.typeSymbolKey(
                "src/main/java/demo/service/OrderService.java",
                "demo.service.OrderService"
        );
        assertThat(graph.symbolDependencies()).containsExactly(
                new CodeSymbolDependencyProjection(
                        serviceKey,
                        portKey,
                        "IMPLEMENTS",
                        "src/main/java/demo/service/OrderService.java"
                )
        );
        assertThat(graph.fileDependencies()).containsExactly(
                new CodeFileDependencyProjection(
                        "src/main/java/demo/service/OrderService.java",
                        "src/main/java/demo/api/OrderPort.java",
                        "IMPORTS"
                )
        );
    }

    @Test
    void reportsMalformedSourceWithoutRejectingValidFiles() throws Exception {
        write("src/Valid.java", "class Valid {}\n");
        write("src/Broken.java", "class Broken {\n");

        CodeGraphProjection graph = new JavaCodeGraphAnalyzer().analyze(
                repository,
                List.of(file("src/Valid.java"), file("src/Broken.java"))
        );

        assertThat(graph.symbols())
                .extracting(CodeSymbolProjection::qualifiedName)
                .containsExactly("Valid");
        assertThat(graph.parseFailures()).singleElement()
                .asString().contains("src/Broken.java");
    }

    @Test
    void distinguishesSameQualifiedTypeDeclaredInDifferentSourceRoots()
            throws Exception {
        write("initial/src/main/java/demo/Application.java", """
                package demo;
                public class Application {}
                """);
        write("complete/src/main/java/demo/Application.java", """
                package demo;
                public class Application {}
                """);

        CodeGraphProjection graph = new JavaCodeGraphAnalyzer().analyze(
                repository,
                List.of(
                        file("initial/src/main/java/demo/Application.java"),
                        file("complete/src/main/java/demo/Application.java")
                )
        );

        assertThat(graph.symbols()).hasSize(2);
        assertThat(graph.symbols())
                .extracting(CodeSymbolProjection::qualifiedName)
                .containsOnly("demo.Application");
        assertThat(graph.symbols())
                .extracting(CodeSymbolProjection::symbolKey)
                .doesNotHaveDuplicates();
    }

    @Test
    void skipsOversizedJavaSourceBeforeParsing() throws Exception {
        write("src/Huge.java", "x".repeat(2 * 1024 * 1024 + 1));

        CodeGraphProjection graph = new JavaCodeGraphAnalyzer().analyze(
                repository, List.of(file("src/Huge.java"))
        );

        assertThat(graph.symbols()).isEmpty();
        assertThat(graph.parseFailures()).singleElement()
                .asString().contains("exceeds 2 MiB");
    }

    private void write(String path, String content) throws Exception {
        Path target = repository.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private GitRepositoryFileProjection file(String path) throws Exception {
        return new GitRepositoryFileProjection(
                path, "blob", Files.size(repository.resolve(path)), "Java"
        );
    }
}
