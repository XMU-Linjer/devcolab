package com.devcollab.knowledgecore.git.application.exception;

public class GitRepositoryNotFoundException extends RuntimeException {
    public GitRepositoryNotFoundException() {
        super("Git 仓库不存在");
    }
}
