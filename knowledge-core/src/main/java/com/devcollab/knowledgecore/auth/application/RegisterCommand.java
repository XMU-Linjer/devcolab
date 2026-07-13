package com.devcollab.knowledgecore.auth.application;

public record RegisterCommand(
        String username,
        String displayName,
        String password
) {
}