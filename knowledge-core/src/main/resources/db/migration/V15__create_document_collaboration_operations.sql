ALTER TABLE documents
    ADD COLUMN collaboration_sequence BIGINT NOT NULL DEFAULT 0;

CREATE TABLE document_collaboration_operations (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    document_sequence BIGINT NOT NULL,
    client_operation_id UUID NOT NULL,
    operation_type VARCHAR(60) NOT NULL,
    operator_user_id UUID NOT NULL REFERENCES user_accounts(id),
    request_fingerprint VARCHAR(64) NOT NULL,
    result_payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_document_collaboration_operation_id
        UNIQUE (document_id, client_operation_id),
    CONSTRAINT uk_document_collaboration_sequence
        UNIQUE (document_id, document_sequence)
);

CREATE INDEX idx_document_collaboration_operations_recovery
    ON document_collaboration_operations(document_id, document_sequence);
