CREATE TABLE github_connections (
    user_id UUID PRIMARY KEY REFERENCES user_accounts(id) ON DELETE CASCADE,
    github_user_id VARCHAR(80) NOT NULL,
    github_login VARCHAR(120) NOT NULL,
    access_token_ciphertext TEXT NOT NULL,
    access_token_nonce VARCHAR(80) NOT NULL,
    scopes VARCHAR(1000) NOT NULL,
    connected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE github_oauth_states (
    state_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_github_oauth_states_expires_at
    ON github_oauth_states(expires_at);

CREATE TABLE github_repository_imports (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    github_repository_id VARCHAR(80) NOT NULL,
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    repository_id UUID REFERENCES git_repositories(id) ON DELETE CASCADE,
    owner_login VARCHAR(120) NOT NULL,
    repository_name VARCHAR(200) NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    selected_branch VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, github_repository_id),
    CONSTRAINT ck_github_import_mapping_complete CHECK (
        (workspace_id IS NULL AND repository_id IS NULL)
        OR (workspace_id IS NOT NULL AND repository_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_github_import_workspace
    ON github_repository_imports(workspace_id);

CREATE UNIQUE INDEX uk_github_import_repository
    ON github_repository_imports(repository_id);
