package com.devcollab.knowledgecore.auth.application;

public record LoginCommand(
        String username,
        String password
) {
}