CREATE TABLE document_change_binding_proposals (
    id UUID PRIMARY KEY,
    change_request_id UUID NOT NULL
        REFERENCES document_change_requests(id) ON DELETE CASCADE,
    client_binding_proposal_id VARCHAR(100) NOT NULL,
    sequence_number INTEGER NOT NULL,
    action VARCHAR(30) NOT NULL,
    repository_id UUID NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    document_id UUID,
    created_document_operation_id UUID,
    binding_id UUID,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_document_change_binding_proposal_sequence
        UNIQUE (change_request_id, sequence_number),
    CONSTRAINT uk_document_change_binding_proposal_client
        UNIQUE (change_request_id, client_binding_proposal_id),
    CONSTRAINT fk_document_change_binding_proposal_created_document
        FOREIGN KEY (created_document_operation_id)
        REFERENCES document_change_operations(id),
    CONSTRAINT ck_document_change_binding_proposal_action
        CHECK (action IN ('UPSERT_BINDING', 'REMOVE_BINDING')),
    CONSTRAINT ck_document_change_binding_proposal_sequence
        CHECK (sequence_number >= 1)
);
