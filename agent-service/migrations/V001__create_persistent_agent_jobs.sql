CREATE SCHEMA IF NOT EXISTS agent_service;

CREATE TABLE IF NOT EXISTS agent_service.schema_migrations (
    version VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE agent_service.agent_jobs (
    id UUID PRIMARY KEY,
    delegation_id UUID NOT NULL UNIQUE,
    created_by_user_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    repository_id UUID NOT NULL,
    revision VARCHAR(64) NOT NULL,
    scope_type VARCHAR(40) NOT NULL,
    scope_payload JSONB NOT NULL,
    user_instruction VARCHAR(2000),
    status VARCHAR(30) NOT NULL,
    result VARCHAR(40),
    total_units INTEGER NOT NULL DEFAULT 1,
    completed_units INTEGER NOT NULL DEFAULT 0,
    failed_units INTEGER NOT NULL DEFAULT 0,
    review_request_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_agent_job_scope CHECK (scope_type = 'CURRENT_FILE'),
    CONSTRAINT ck_agent_job_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_agent_job_result CHECK (
        result IS NULL OR result IN (
            'NO_CHANGE', 'REVIEW_SUBMITTED', 'PARTIALLY_COMPLETED'
        )
    )
);

CREATE TABLE agent_service.agent_units (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES agent_service.agent_jobs(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    phase VARCHAR(40),
    attempt INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    worker_id VARCHAR(200),
    lease_expires_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    result VARCHAR(40),
    review_request_id UUID,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_agent_unit_job_ordinal UNIQUE (job_id, ordinal),
    CONSTRAINT ck_agent_unit_ordinal CHECK (ordinal >= 1),
    CONSTRAINT ck_agent_unit_status CHECK (
        status IN (
            'PENDING', 'CLAIMED', 'RUNNING', 'RETRY_WAITING',
            'COMPLETED', 'FAILED', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_agent_unit_phase CHECK (
        phase IS NULL OR phase IN (
            'LOADING_CONTEXT', 'MODEL_RUNNING', 'VALIDATING',
            'REPAIRING', 'SUBMITTING_REVIEW'
        )
    ),
    CONSTRAINT ck_agent_unit_result CHECK (
        result IS NULL OR result IN ('NO_CHANGE', 'REVIEW_SUBMITTED')
    )
);

CREATE INDEX ix_agent_units_claim
    ON agent_service.agent_units(status, next_attempt_at, lease_expires_at, created_at);

CREATE TABLE agent_service.worker_heartbeats (
    worker_id VARCHAR(200) PRIMARY KEY,
    heartbeat_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ NOT NULL
);
