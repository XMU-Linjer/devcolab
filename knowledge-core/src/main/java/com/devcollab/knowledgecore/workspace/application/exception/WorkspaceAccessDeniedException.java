package com.devcollab.knowledgecore.workspace.application.exception;

public class WorkspaceAccessDeniedException extends RuntimeException {

    public WorkspaceAccessDeniedException() {
        super("无权访问该工作空间");
    }
}
