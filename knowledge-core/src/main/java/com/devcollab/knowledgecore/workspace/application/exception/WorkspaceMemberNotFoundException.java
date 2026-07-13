package com.devcollab.knowledgecore.workspace.application.exception;

public class WorkspaceMemberNotFoundException extends RuntimeException {

    public WorkspaceMemberNotFoundException() {
        super("工作区成员不存在");
    }
}
