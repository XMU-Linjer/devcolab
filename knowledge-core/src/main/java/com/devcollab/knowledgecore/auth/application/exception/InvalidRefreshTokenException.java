package com.devcollab.knowledgecore.auth.application.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("刷新令牌无效、已撤销或已过期");
    }
}
