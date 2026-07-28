package com.devcollab.knowledgecore.agentdelegation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAgentDelegationRepository implements AgentDelegationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAgentDelegationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentDelegation save(AgentDelegation delegation) {
        jdbcTemplate.update(
                """
                INSERT INTO agent_delegations (
                    id, job_id, created_by_user_id, workspace_id, repository_id,
                    revision, allowed_tools, status, created_at, expires_at, revoked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                """,
                delegation.id(), delegation.jobId(), delegation.createdByUserId(),
                delegation.workspaceId(), delegation.repositoryId(), delegation.revision(),
                writeTools(delegation.allowedTools()), delegation.status(),
                Timestamp.from(delegation.createdAt()), Timestamp.from(delegation.expiresAt()),
                delegation.revokedAt() == null ? null : Timestamp.from(delegation.revokedAt())
        );
        return delegation;
    }

    @Override
    public Optional<AgentDelegation> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT * FROM agent_delegations WHERE id = ?",
                (rs, rowNum) -> new AgentDelegation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("job_id", UUID.class),
                        rs.getObject("created_by_user_id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("repository_id", UUID.class),
                        rs.getString("revision"),
                        readTools(rs.getString("allowed_tools")),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null
                                ? null : rs.getTimestamp("revoked_at").toInstant()
                ),
                id
        ).stream().findFirst();
    }

    private String writeTools(List<String> tools) {
        try {
            return objectMapper.writeValueAsString(tools);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize Agent delegation tools", exception);
        }
    }

    private List<String> readTools(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize Agent delegation tools", exception);
        }
    }
}
