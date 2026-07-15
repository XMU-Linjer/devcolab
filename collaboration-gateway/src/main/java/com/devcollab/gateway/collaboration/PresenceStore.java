package com.devcollab.gateway.collaboration;

import com.devcollab.gateway.auth.GatewayTokenService.GatewayUser;
import com.devcollab.gateway.collaboration.CollaborationMessages.EditingState;
import com.devcollab.gateway.collaboration.CollaborationMessages.PresenceMember;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class PresenceStore {

    private static final Logger log = LoggerFactory.getLogger(PresenceStore.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final GatewayProperties properties;

    public PresenceStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            GatewayProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<PresenceMember> join(
            UUID documentId,
            String sessionId,
            GatewayUser user
    ) {
        PresenceMember member = new PresenceMember(
                sessionId,
                user.userId(),
                user.username(),
                Instant.now()
        );
        redisTemplate.opsForHash().put(
                presenceKey(documentId),
                sessionId,
                write(member)
        );
        redisTemplate.expire(presenceKey(documentId), properties.presenceTtl());
        return members(documentId);
    }

    public List<PresenceMember> touch(UUID documentId) {
        redisTemplate.expire(presenceKey(documentId), properties.presenceTtl());
        redisTemplate.expire(editingKey(documentId), properties.editingTtl());
        return members(documentId);
    }

    public List<PresenceMember> leave(UUID documentId, String sessionId) {
        redisTemplate.opsForHash().delete(presenceKey(documentId), sessionId);
        redisTemplate.expire(presenceKey(documentId), properties.presenceTtl());
        return members(documentId);
    }

    public List<PresenceMember> members(UUID documentId) {
        return redisTemplate.opsForHash()
                .values(presenceKey(documentId))
                .stream()
                .map(String::valueOf)
                .map(this::readPresence)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<EditingState> startEditing(
            UUID documentId,
            UUID blockId,
            GatewayUser user
    ) {
        EditingState state = new EditingState(
                blockId,
                user.userId(),
                user.username(),
                Instant.now()
        );
        redisTemplate.opsForHash().put(
                editingKey(documentId),
                blockId.toString(),
                write(state)
        );
        redisTemplate.expire(editingKey(documentId), properties.editingTtl());
        return editingStates(documentId);
    }

    public List<EditingState> stopEditing(UUID documentId, UUID blockId) {
        redisTemplate.opsForHash().delete(editingKey(documentId), blockId.toString());
        redisTemplate.expire(editingKey(documentId), properties.editingTtl());
        return editingStates(documentId);
    }

    public List<EditingState> stopEditingByUser(UUID documentId, UUID userId) {
        String key = editingKey(documentId);
        redisTemplate.opsForHash()
                .entries(key)
                .forEach((blockId, value) -> {
                    EditingState state = readEditing(String.valueOf(value));
                    if (state != null && state.userId().equals(userId)) {
                        redisTemplate.opsForHash().delete(key, blockId);
                    }
                });
        redisTemplate.expire(key, properties.editingTtl());
        return editingStates(documentId);
    }

    public List<EditingState> editingStates(UUID documentId) {
        return redisTemplate.opsForHash()
                .values(editingKey(documentId))
                .stream()
                .map(String::valueOf)
                .map(this::readEditing)
                .filter(Objects::nonNull)
                .toList();
    }

    private String presenceKey(UUID documentId) {
        return "gateway:document:" + documentId + ":presence";
    }

    private String editingKey(UUID documentId) {
        return "gateway:document:" + documentId + ":editing";
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize gateway state", e);
        }
    }

    private PresenceMember readPresence(String value) {
        try {
            return objectMapper.readValue(value, PresenceMember.class);
        } catch (Exception e) {
            log.warn("Skipping malformed presence entry");
            log.debug("Malformed presence entry detail", e);
            return null;
        }
    }

    private EditingState readEditing(String value) {
        try {
            return objectMapper.readValue(value, EditingState.class);
        } catch (Exception e) {
            log.warn("Skipping malformed editing entry");
            log.debug("Malformed editing entry detail", e);
            return null;
        }
    }
}
