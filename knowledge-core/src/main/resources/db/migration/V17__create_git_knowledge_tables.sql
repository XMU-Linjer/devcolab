CREATE TABLE git_repositories (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    remote_url VARCHAR(500) NOT NULL,
    default_branch VARCHAR(200) NOT NULL,
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_git_repository_remote UNIQUE (workspace_id, remote_url)
);

CREATE TABLE git_changes (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES git_repositories(id) ON DELETE CASCADE,
    change_type VARCHAR(30) NOT NULL,
    external_id VARCHAR(200) NOT NULL,
    title VARCHAR(500) NOT NULL,
    commit_sha VARCHAR(64) NOT NULL,
    base_ref VARCHAR(200),
    head_ref VARCHAR(200),
    author_name VARCHAR(200),
    web_url VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_git_change_external UNIQUE (repository_id, change_type, external_id)
);

CREATE TABLE git_file_diffs (
    id UUID PRIMARY KEY,
    git_change_id UUID NOT NULL REFERENCES git_changes(id) ON DELETE CASCADE,
    path VARCHAR(1000) NOT NULL,
    old_path VARCHAR(1000),
    change_type VARCHAR(30) NOT NULL,
    additions INTEGER NOT NULL,
    deletions INTEGER NOT NULL,
    patch_excerpt TEXT
);

CREATE INDEX idx_git_file_diffs_change ON git_file_diffs(git_change_id);
CREATE INDEX idx_git_file_diffs_path ON git_file_diffs(path);

CREATE TABLE code_document_bindings (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES git_repositories(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    block_id UUID REFERENCES document_blocks(id) ON DELETE CASCADE,
    target_key VARCHAR(40) NOT NULL,
    path_pattern VARCHAR(1000) NOT NULL,
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_code_document_binding UNIQUE (
        repository_id, document_id, target_key, path_pattern
    )
);

CREATE INDEX idx_code_binding_repository ON code_document_bindings(repository_id);
CREATE INDEX idx_code_binding_document ON code_document_bindings(document_id);
