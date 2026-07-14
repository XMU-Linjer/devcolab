ALTER TABLE documents
    ADD COLUMN document_type VARCHAR(30) NOT NULL DEFAULT 'REQUIREMENT';

CREATE INDEX idx_documents_document_type
    ON documents(document_type);
