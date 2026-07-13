package com.devcollab.knowledgecore.common.outbox.application;

import com.devcollab.knowledgecore.common.outbox.domain.OutboxEvent;

public interface OutboxEventHandler {

    void handle(OutboxEvent event);
}
