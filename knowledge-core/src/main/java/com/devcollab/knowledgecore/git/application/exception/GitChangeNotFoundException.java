package com.devcollab.knowledgecore.git.application.exception;

public class GitChangeNotFoundException extends RuntimeException {
    public GitChangeNotFoundException() {
        super("Git 变更不存在");
    }
}
