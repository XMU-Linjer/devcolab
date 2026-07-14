ALTER TABLE documents
    ADD COLUMN review_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT';

CREATE INDEX idx_documents_review_status
    ON documents(review_status);
