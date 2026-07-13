package com.devcollab.knowledgecore.workspace.application.exception;

public class WorkspaceLastAdminException extends RuntimeException {

    public WorkspaceLastAdminException() {
        super("工作区至少需要保留一名管理员");
    }
}
