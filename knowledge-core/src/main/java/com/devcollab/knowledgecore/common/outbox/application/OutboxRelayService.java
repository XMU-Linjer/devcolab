package com.devcollab.knowledgecore.common.outbox.application;

import com.devcollab.knowledgecore.common.outbox.domain.OutboxEvent;
import com.devcollab.knowledgecore.common.outbox.domain.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxRelayService {

    private static final int DEFAULT_BATCH_SIZE = 50;

    private final OutboxEventRepository eventRepository;
    private final OutboxEventHandler eventHandler;

    public OutboxRelayService(
            OutboxEventRepository eventRepository,
            OutboxEventHandler eventHandler
    ) {
        this.eventRepository = eventRepository;
        this.eventHandler = eventHandler;
    }

    @Transactional
    public OutboxRelayResult relayPendingEvents() {
        return relayPendingEvents(DEFAULT_BATCH_SIZE);
    }

    @Transactional
    public OutboxRelayResult relayPendingEvents(int batchSize) {
        List<OutboxEvent> pendingEvents = eventRepository.findPending(
                batchSize
        );
        int published = 0;
        int failed = 0;

        for (OutboxEvent event : pendingEvents) {
            try {
                eventHandler.handle(event);
                eventRepository.markPublished(event.id());
                published++;
            } catch (RuntimeException exception) {
                eventRepository.markFailed(event.id(), safeMessage(exception));
                failed++;
            }
        }

        return new OutboxRelayResult(
                pendingEvents.size(),
                published,
                failed
        );
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 500
                ? message
                : message.substring(0, 500);
    }
}
