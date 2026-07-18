ALTER TABLE document_blocks
    ADD COLUMN content_schema_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE document_blocks
    ADD COLUMN content_json TEXT;
