CREATE TABLE agent_delegations (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE,
    created_by_user_id UUID NOT NULL REFERENCES user_accounts(id),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES git_repositories(id) ON DELETE CASCADE,
    revision VARCHAR(64) NOT NULL,
    allowed_tools JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_agent_delegation_status
        CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX ix_agent_delegations_job
    ON agent_delegations(job_id, status, expires_at);
