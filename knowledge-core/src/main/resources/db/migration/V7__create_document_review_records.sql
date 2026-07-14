CREATE TABLE document_review_records (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    action VARCHAR(30) NOT NULL,
    comment TEXT,
    operator_user_id UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_document_review_records_document_id
    ON document_review_records(document_id, created_at DESC);
