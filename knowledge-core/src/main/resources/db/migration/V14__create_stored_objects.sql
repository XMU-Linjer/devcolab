CREATE TABLE stored_objects (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    owner_user_id UUID NOT NULL REFERENCES user_accounts(id),
    object_type VARCHAR(40) NOT NULL,
    bucket VARCHAR(120) NOT NULL,
    object_key VARCHAR(700) NOT NULL UNIQUE,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    reference_type VARCHAR(40) NOT NULL,
    reference_id UUID NOT NULL,
    source_event_id UUID NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_stored_objects_workspace
    ON stored_objects(workspace_id, created_at);

CREATE INDEX idx_stored_objects_reference
    ON stored_objects(reference_type, reference_id);
