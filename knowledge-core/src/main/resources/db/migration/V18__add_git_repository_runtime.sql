ALTER TABLE git_repositories
    ADD COLUMN sync_status VARCHAR(30) NOT NULL DEFAULT 'REGISTERED';

ALTER TABLE git_repositories
    ADD COLUMN last_synced_commit VARCHAR(64);

ALTER TABLE git_repositories
    ADD COLUMN last_synced_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE git_repositories
    ADD COLUMN last_sync_error VARCHAR(1000);

CREATE TABLE git_repository_files (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES git_repositories(id) ON DELETE CASCADE,
    path VARCHAR(1000) NOT NULL,
    blob_sha VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    language VARCHAR(80),
    CONSTRAINT uk_git_repository_file UNIQUE (repository_id, path)
);

CREATE INDEX idx_git_repository_file_repository
    ON git_repository_files(repository_id);

