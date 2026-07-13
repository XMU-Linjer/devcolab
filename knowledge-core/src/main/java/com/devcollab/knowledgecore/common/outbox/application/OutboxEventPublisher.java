package com.devcollab.knowledgecore.common.outbox.application;

import com.devcollab.knowledgecore.common.outbox.domain.OutboxEvent;
import com.devcollab.knowledgecore.common.outbox.domain.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxEventPublisher {

    private final OutboxEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(
            OutboxEventRepository eventRepository,
            ObjectMapper objectMapper
    ) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    public OutboxEvent publish(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload
    ) {
        return eventRepository.save(OutboxEvent.pending(
                aggregateType,
                aggregateId,
                eventType,
                writePayload(payload),
                Instant.now()
        ));
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Outbox event payload cannot be serialized",
                    exception
            );
        }
    }
}
