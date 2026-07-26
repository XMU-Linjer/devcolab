CREATE TABLE document_change_requests (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    client_request_id VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    rationale TEXT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    submitted_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_by UUID REFERENCES user_accounts(id),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    rejection_reason VARCHAR(2000),
    CONSTRAINT uk_document_change_request_client
        UNIQUE (workspace_id, submitted_by, client_request_id),
    CONSTRAINT ck_document_change_request_status
        CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED', 'STALE'))
);

CREATE INDEX idx_document_change_request_workspace_status_created
    ON document_change_requests(workspace_id, status, created_at DESC, id DESC);

CREATE TABLE document_change_operations (
    id UUID PRIMARY KEY,
    change_request_id UUID NOT NULL
        REFERENCES document_change_requests(id) ON DELETE CASCADE,
    client_operation_id VARCHAR(100) NOT NULL,
    sequence_number INTEGER NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    document_id UUID,
    created_document_operation_id UUID,
    block_id UUID,
    base_block_version BIGINT,
    original_block_type VARCHAR(30),
    original_plain_text TEXT,
    original_content_schema_version INTEGER,
    original_content_json TEXT,
    original_sort_order INTEGER,
    proposed_document_title VARCHAR(200),
    proposed_document_type VARCHAR(30),
    proposed_parent_document_id UUID,
    proposed_block_type VARCHAR(30),
    proposed_plain_text TEXT,
    proposed_content_schema_version INTEGER,
    proposed_content_json TEXT,
    CONSTRAINT uk_document_change_operation_sequence
        UNIQUE (change_request_id, sequence_number),
    CONSTRAINT uk_document_change_operation_client
        UNIQUE (change_request_id, client_operation_id),
    CONSTRAINT fk_document_change_operation_created_document
        FOREIGN KEY (created_document_operation_id)
        REFERENCES document_change_operations(id),
    CONSTRAINT ck_document_change_operation_type
        CHECK (operation_type IN (
            'CREATE_DOCUMENT', 'ADD_BLOCK', 'UPDATE_BLOCK', 'DELETE_BLOCK'
        )),
    CONSTRAINT ck_document_change_operation_sequence
        CHECK (sequence_number >= 1),
    CONSTRAINT ck_document_change_operation_base_version
        CHECK (base_block_version IS NULL OR base_block_version >= 0)
);

CREATE TABLE document_change_evidence (
    id UUID PRIMARY KEY,
    change_request_id UUID NOT NULL
        REFERENCES document_change_requests(id) ON DELETE CASCADE,
    operation_id UUID
        REFERENCES document_change_operations(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL,
    commit_hash VARCHAR(64),
    file_path VARCHAR(1000) NOT NULL,
    start_line INTEGER,
    end_line INTEGER,
    description VARCHAR(1000) NOT NULL,
    blob_sha VARCHAR(64),
    excerpt_text TEXT,
    excerpt_hash VARCHAR(64),
    CONSTRAINT ck_document_change_evidence_lines CHECK (
        (start_line IS NULL AND end_line IS NULL)
        OR
        (start_line >= 1 AND end_line >= start_line)
    )
);

CREATE INDEX idx_document_change_evidence_request_operation
    ON document_change_evidence(change_request_id, operation_id);

CREATE INDEX idx_document_change_evidence_repository_path
    ON document_change_evidence(repository_id, commit_hash, file_path);
