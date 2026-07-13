package com.devcollab.knowledgecore.workspace.application.exception;

public class WorkspaceMemberAlreadyExistsException extends RuntimeException {

    public WorkspaceMemberAlreadyExistsException() {
        super("用户已经是工作区成员");
    }
}
