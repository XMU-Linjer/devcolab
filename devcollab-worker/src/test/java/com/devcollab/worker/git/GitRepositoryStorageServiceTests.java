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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitRepositoryStorageServiceTests {

    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Path.of("target", "git-storage-tests", UUID.randomUUID().toString())
                .toAbsolutePath().normalize();
        Files.createDirectories(tempDir);
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (!Files.exists(tempDir)) return;
        try (var paths = Files.walk(tempDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void deletesOnlyRepositoryUuidDirectory() throws Exception {
        GitRepositoryProjectionStore store = mock(GitRepositoryProjectionStore.class);
        GitRepositoryStorageService service = service(store);
        UUID workspaceId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        Path repository = tempDir.resolve(workspaceId.toString())
                .resolve(repositoryId.toString()).resolve("repository");
        Files.createDirectories(repository);
        Path trackedFile = repository.resolve("README.md");
        Files.writeString(trackedFile, "hello");
        Files.setAttribute(trackedFile, "dos:readonly", true);
        Path neighbor = tempDir.resolve("keep.txt");
        Files.writeString(neighbor, "keep");

        service.delete(workspaceId, repositoryId);

        assertThat(repository.getParent()).doesNotExist();
        assertThat(neighbor).exists();
    }

    @Test
    void rejectsNonAllowlistedRemoteAndRecordsFailure() {
        GitRepositoryProjectionStore store = mock(GitRepositoryProjectionStore.class);
        UUID repositoryId = UUID.randomUUID();
        when(store.markSyncing(repositoryId)).thenReturn(true);
        GitRepositoryStorageService service = service(store);

        assertThatThrownBy(() -> service.synchronize(
                UUID.randomUUID(), repositoryId,
                "https://internal.example/repository.git", "main"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("synchronization failed");

        verify(store).markFailed(
                repositoryId,
                "Only public HTTPS repositories on allowed hosts are supported"
        );
    }

    private GitRepositoryStorageService service(GitRepositoryProjectionStore store) {
        return new GitRepositoryStorageService(
                new GitRepositoryStorageProperties(
                        true, "test", tempDir, List.of("github.com"),
                        10, 100, 10, 10_000_000
                ),
                store
        );
    }
}
