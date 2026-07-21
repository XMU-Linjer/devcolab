package com.devcollab.knowledgecore.git.application.exception;

public class GitRepositoryAlreadyExistsException extends RuntimeException {
    public GitRepositoryAlreadyExistsException() {
        super("该远程仓库已绑定到当前工作区");
    }
}
