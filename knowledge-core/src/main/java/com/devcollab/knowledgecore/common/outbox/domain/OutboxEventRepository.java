package com.devcollab.knowledgecore.common.outbox.domain;

import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);

    List<OutboxEvent> findAll();
}
