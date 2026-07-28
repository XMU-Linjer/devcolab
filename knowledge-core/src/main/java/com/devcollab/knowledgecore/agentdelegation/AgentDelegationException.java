package com.devcollab.knowledgecore.agentdelegation;

import org.springframework.http.HttpStatus;

public class AgentDelegationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AgentDelegationException(HttpStatus status, String code, String message) {
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
