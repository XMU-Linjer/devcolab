package com.devcollab.knowledgecore.auth.application.exception;

public class InvalidCsrfTokenException extends RuntimeException {

    public InvalidCsrfTokenException() {
        super("CSRF 校验失败");
    }
}
