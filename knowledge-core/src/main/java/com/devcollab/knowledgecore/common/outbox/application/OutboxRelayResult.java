package com.devcollab.knowledgecore.common.outbox.application;

public record OutboxRelayResult(
        int scanned,
        int published,
        int failed
) {
}
