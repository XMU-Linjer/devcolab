package com.devcollab.knowledgecore.workspace.infrastructure;

import com.devcollab.knowledgecore.workspace.domain.WorkspaceMember;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMemberRepository;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcWorkspaceMemberRepository
        implements WorkspaceMemberRepository {

    private static final RowMapper<WorkspaceMember> MEMBER_ROW_MAPPER =
            (rs, rowNum) -> new WorkspaceMember(
                    rs.getObject("workspace_id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    WorkspaceRole.valueOf(rs.getString("role")),
                    rs.getTimestamp("joined_at").toInstant()
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkspaceMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public WorkspaceMember save(WorkspaceMember member) {
        int updated = jdbcTemplate.update("""
                        UPDATE workspace_members SET role = ?, joined_at = ?
                         WHERE workspace_id = ? AND user_id = ?
                        """,
                member.role().name(), Timestamp.from(member.joinedAt()),
                member.workspaceId(), member.userId());

        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO workspace_members
                                (workspace_id, user_id, role, joined_at)
                            VALUES (?, ?, ?, ?)
                            """,
                    member.workspaceId(), member.userId(), member.role().name(),
                    Timestamp.from(member.joinedAt()));
        }
        return member;
    }

    @Override
    public Optional<WorkspaceMember> findByWorkspaceIdAndUserId(
            UUID workspaceId,
            UUID userId
    ) {
        return jdbcTemplate.query("""
                        SELECT * FROM workspace_members
                         WHERE workspace_id = ? AND user_id = ?
                        """,
                MEMBER_ROW_MAPPER,
                workspaceId,
                userId
        ).stream().findFirst();
    }
}
