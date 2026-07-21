package com.devcollab.worker.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class GitRepositoryStorageService {

    private final GitRepositoryStorageProperties properties;
    private final GitRepositoryProjectionStore store;

    public GitRepositoryStorageService(
            GitRepositoryStorageProperties properties,
            GitRepositoryProjectionStore store
    ) {
        this.properties = properties;
        this.store = store;
    }

    public void synchronize(
            UUID workspaceId,
            UUID repositoryId,
            String remoteUrl,
            String defaultBranch
    ) {
        if (!store.markSyncing(repositoryId)) {
            return;
        }
        Path repositoryDirectory = repositoryDirectory(workspaceId, repositoryId);
        try {
            validateRemote(remoteUrl);
            Files.createDirectories(repositoryDirectory.getParent());
            try (Git git = openOrClone(
                    repositoryDirectory, remoteUrl, defaultBranch
            )) {
                verifyRepositorySize(repositoryDirectory);
                Repository repository = git.getRepository();
                List<GitRepositoryFileProjection> files = scanFiles(repository);
                List<GitCommitProjection> commits = scanCommits(git, repository);
                String head = repository.resolve("HEAD").name();
                store.replaceFiles(repositoryId, files);
                store.saveCommits(repositoryId, commits);
                store.markReady(repositoryId, head);
            }
        } catch (Exception exception) {
            store.markFailed(repositoryId, rootMessage(exception));
            throw new IllegalStateException(
                    "Git repository synchronization failed repository=" + repositoryId,
                    exception
            );
        }
    }

    public void delete(UUID workspaceId, UUID repositoryId) {
        Path directory = repositoryDirectory(workspaceId, repositoryId).getParent();
        if (!Files.exists(directory)) {
            return;
        }
        try {
            deleteDirectory(directory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Git repository directory deletion failed repository=" + repositoryId,
                    exception
            );
        }
    }

    Path repositoryDirectory(UUID workspaceId, UUID repositoryId) {
        Path root = properties.dataRoot().toAbsolutePath().normalize();
        Path target = root.resolve(workspaceId.toString())
                .resolve(repositoryId.toString())
                .resolve("repository")
                .normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Repository storage path escapes data root");
        }
        return target;
    }

    private Git openOrClone(
            Path directory,
            String remoteUrl,
            String defaultBranch
    ) throws Exception {
        if (Files.isDirectory(directory.resolve(".git"))) {
            Git git = Git.open(directory.toFile());
            git.fetch()
                    .setRemoveDeletedRefs(true)
                    .setTimeout(properties.timeoutSeconds())
                    .call();
            String remoteRef = "refs/remotes/origin/" + defaultBranch;
            if (git.getRepository().resolve(remoteRef) == null) {
                git.close();
                throw new IllegalArgumentException(
                        "Default branch does not exist: " + defaultBranch
                );
            }
            git.reset().setMode(ResetCommand.ResetType.HARD)
                    .setRef(remoteRef).call();
            return git;
        }
        if (Files.exists(directory)) {
            deleteDirectory(directory);
        }
        return Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(directory.toFile())
                .setBranch(defaultBranch)
                .setCloneAllBranches(false)
                .setTimeout(properties.timeoutSeconds())
                .call();
    }

    private List<GitRepositoryFileProjection> scanFiles(Repository repository)
            throws IOException {
        ObjectId treeId = repository.resolve("HEAD^{tree}");
        if (treeId == null) {
            throw new IllegalStateException("Repository HEAD tree is unavailable");
        }
        List<GitRepositoryFileProjection> files = new ArrayList<>();
        try (TreeWalk walk = new TreeWalk(repository)) {
            walk.addTree(treeId);
            walk.setRecursive(true);
            while (walk.next()) {
                if (files.size() >= properties.maxFiles()) {
                    throw new IllegalStateException(
                            "Repository file count exceeds limit " + properties.maxFiles()
                    );
                }
                ObjectId objectId = walk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);
                String path = walk.getPathString();
                files.add(new GitRepositoryFileProjection(
                        path, objectId.name(), loader.getSize(), language(path)
                ));
            }
        }
        return List.copyOf(files);
    }

    private List<GitCommitProjection> scanCommits(Git git, Repository repository)
            throws Exception {
        List<GitCommitProjection> commits = new ArrayList<>();
        Iterable<RevCommit> log = git.log()
                .setMaxCount(properties.maxCommits())
                .call();
        try (RevWalk revWalk = new RevWalk(repository);
             DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            for (RevCommit commit : log) {
                AbstractTreeIterator previous = commit.getParentCount() == 0
                        ? new EmptyTreeIterator()
                        : treeIterator(repository, revWalk.parseCommit(
                                commit.getParent(0).getId()
                        ));
                AbstractTreeIterator current = treeIterator(repository, commit);
                List<GitDiffProjection> diffs = formatter.scan(previous, current)
                        .stream().map(this::toProjection).toList();
                commits.add(new GitCommitProjection(
                        commit.name(), commit.getShortMessage(),
                        commit.getAuthorIdent() == null ? null
                                : commit.getAuthorIdent().getName(),
                        Instant.ofEpochSecond(commit.getCommitTime()), diffs
                ));
            }
        }
        return List.copyOf(commits);
    }

    private AbstractTreeIterator treeIterator(
            Repository repository,
            RevCommit commit
    ) throws IOException {
        CanonicalTreeParser parser = new CanonicalTreeParser();
        try (var reader = repository.newObjectReader()) {
            parser.reset(reader, commit.getTree().getId());
        }
        return parser;
    }

    private GitDiffProjection toProjection(DiffEntry entry) {
        return switch (entry.getChangeType()) {
            case ADD -> new GitDiffProjection(
                    entry.getNewPath(), null, "ADDED"
            );
            case DELETE -> new GitDiffProjection(
                    entry.getOldPath(), null, "DELETED"
            );
            case RENAME, COPY -> new GitDiffProjection(
                    entry.getNewPath(), entry.getOldPath(), "RENAMED"
            );
            case MODIFY -> new GitDiffProjection(
                    entry.getNewPath(), null, "MODIFIED"
            );
        };
    }

    private void validateRemote(String remoteUrl) {
        URI uri;
        try {
            uri = URI.create(remoteUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Git remote URL", exception);
        }
        boolean allowedHost = properties.allowedHosts().stream()
                .anyMatch(host -> host.equalsIgnoreCase(uri.getHost()));
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getUserInfo() != null
                || !allowedHost
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getPath() == null || uri.getPath().isBlank()) {
            throw new IllegalArgumentException(
                    "Only public HTTPS repositories on allowed hosts are supported"
            );
        }
    }

    private void verifyRepositorySize(Path directory) throws IOException {
        long total;
        try (var paths = Files.walk(directory)) {
            total = paths.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException exception) {
                    throw new RepositorySizeException(exception);
                }
            }).sum();
        } catch (RepositorySizeException exception) {
            throw (IOException) exception.getCause();
        }
        if (total > properties.maxRepositoryBytes()) {
            deleteDirectory(directory);
            throw new IllegalStateException(
                    "Repository size exceeds limit " + properties.maxRepositoryBytes()
            );
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                clearReadOnly(path);
                Files.deleteIfExists(path);
            }
        }
    }

    private void clearReadOnly(Path path) throws IOException {
        try {
            Boolean readOnly = (Boolean) Files.getAttribute(path, "dos:readonly");
            if (Boolean.TRUE.equals(readOnly)) {
                Files.setAttribute(path, "dos:readonly", false);
            }
        } catch (UnsupportedOperationException ignored) {
            // Non-Windows file systems do not expose DOS attributes.
        }
    }

    private String language(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            return null;
        }
        return switch (lower.substring(dot + 1)) {
            case "java" -> "Java";
            case "kt", "kts" -> "Kotlin";
            case "ts", "tsx" -> "TypeScript";
            case "js", "jsx", "mjs" -> "JavaScript";
            case "py" -> "Python";
            case "go" -> "Go";
            case "rs" -> "Rust";
            case "vue" -> "Vue";
            case "sql" -> "SQL";
            case "md" -> "Markdown";
            case "yml", "yaml" -> "YAML";
            default -> null;
        };
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static class RepositorySizeException extends RuntimeException {
        RepositorySizeException(IOException cause) {
            super(cause);
        }
    }
}
