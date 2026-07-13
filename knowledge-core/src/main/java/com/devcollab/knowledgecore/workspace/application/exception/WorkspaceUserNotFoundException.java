package com.devcollab.knowledgecore.workspace.application.exception;

public class WorkspaceUserNotFoundException extends RuntimeException {

    public WorkspaceUserNotFoundException() {
        super("用户不存在");
    }
}
