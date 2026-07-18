package com.devcollab.knowledgecore.document.api;

import com.fasterxml.jackson.databind.JsonNode;

public record DocumentBlockContentResponse(
        String text,
        int schemaVersion,
        JsonNode document
) {
}
