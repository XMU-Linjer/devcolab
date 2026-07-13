package com.devcollab.knowledgecore.auth.application.exception;

public class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException() {
        super("用户名已存在");
    }
}
