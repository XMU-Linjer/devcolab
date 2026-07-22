package com.devcollab.knowledgecore.git.application.exception;

public class GitRepositoryFileNotFoundException extends RuntimeException {

    public GitRepositoryFileNotFoundException() {
        super("仓库文件不存在");
    }
}
