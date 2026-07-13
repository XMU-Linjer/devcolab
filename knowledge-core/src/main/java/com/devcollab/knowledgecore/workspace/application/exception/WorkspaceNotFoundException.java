package com.devcollab.knowledgecore.workspace.application.exception;

public class WorkspaceNotFoundException extends RuntimeException {

    public WorkspaceNotFoundException() {
        super("工作空间不存在");
    }
}
