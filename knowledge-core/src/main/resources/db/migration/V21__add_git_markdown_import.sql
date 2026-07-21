ALTER TABLE git_repository_files
    ADD COLUMN content_text TEXT;

CREATE TABLE git_document_imports (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES git_repositories(id) ON DELETE CASCADE,
    source_path VARCHAR(1000) NOT NULL,
    source_blob_sha VARCHAR(64) NOT NULL,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_git_document_import_source
        UNIQUE (repository_id, source_path)
);

CREATE INDEX idx_git_document_import_workspace
    ON git_document_imports(workspace_id, repository_id);
