CREATE TABLE document_versions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL,
    title VARCHAR(200) NOT NULL,
    snapshot_payload TEXT NOT NULL,
    published_by UUID NOT NULL REFERENCES user_accounts(id),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (document_id, version_no)
);

CREATE INDEX idx_document_versions_document_id
    ON document_versions(document_id, version_no);
