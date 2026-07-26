package com.devcollab.knowledgecore.documentchange.application;

import org.springframework.http.HttpStatus;

public class DocumentChangeException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public DocumentChangeException(
            HttpStatus status,
            String code,
            String message
    ) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
